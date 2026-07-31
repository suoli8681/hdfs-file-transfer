package com.hdfs.transfer.agent.executor;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.monitor.LogCollector;
import com.hdfs.transfer.agent.precheck.PreCheckService;
import com.hdfs.transfer.agent.retry.RetryHandler;
import com.hdfs.transfer.agent.verify.DataVerifier;
import com.hdfs.transfer.common.dto.TaskProgressDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TaskExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionManager.class);
    // Matches Hadoop MapReduce progress: "map 75% reduce 0%"
    private static final Pattern MR_PROGRESS =
            Pattern.compile("map\\s+(\\d+)%\\s+reduce\\s+(\\d+)%");
    private static final Pattern GENERIC_PERCENT =
            Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final Pattern FILES_COPIED =
            Pattern.compile("(?:Copied|copied)\\s+(\\d+)\\s+files?");
    private static final Pattern BYTES_LINE =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(KB|MB|GB|TB|bytes?)");

    private final AgentConfig agentConfig;
    private final ShellScriptGenerator scriptGenerator;
    private final ShellProcessManager processManager;
    private final LogCollector logCollector;
    private final DataVerifier dataVerifier;
    private final PreCheckService preCheckService;
    private final RetryHandler retryHandler;
    private final ServerCommunicator communicator;
    private final TaskStateStore taskStateStore;
    private final SourceFileLister sourceFileLister;

    private final Map<Long, TaskProgressDTO> taskProgressMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastReportTimeMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> reportLineCountMap = new ConcurrentHashMap<>();
    private final Map<Long, String> taskTargetPathMap = new ConcurrentHashMap<>();
    private final Map<Long, String> taskSourcePathMap = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> taskFileListMap = new ConcurrentHashMap<>();
    private final Set<Long> killedTaskIds = ConcurrentHashMap.newKeySet();

    public TaskExecutionManager(AgentConfig agentConfig, ShellScriptGenerator scriptGenerator,
                                ShellProcessManager processManager, LogCollector logCollector,
                                DataVerifier dataVerifier, PreCheckService preCheckService,
                                RetryHandler retryHandler, ServerCommunicator communicator,
                                TaskStateStore taskStateStore, SourceFileLister sourceFileLister) {
        this.agentConfig = agentConfig;
        this.scriptGenerator = scriptGenerator;
        this.processManager = processManager;
        this.logCollector = logCollector;
        this.dataVerifier = dataVerifier;
        this.preCheckService = preCheckService;
        this.retryHandler = retryHandler;
        this.communicator = communicator;
        this.taskStateStore = taskStateStore;
        this.sourceFileLister = sourceFileLister;
    }

    public void executeTask(Long taskId, String sourcePath, String targetPath,
                            String sourceCluster, String targetCluster,
                            String distcpOptions) {

        sourcePath = PathExpressionResolver.resolve(sourcePath);
        targetPath = PathExpressionResolver.resolve(targetPath);
        log.info("Task {} resolved paths: source={}, target={}", taskId, sourcePath, targetPath);

        taskTargetPathMap.put(taskId, targetPath);
        taskSourcePathMap.put(taskId, sourcePath);

        if (killedTaskIds.contains(taskId)) {
            log.info("Task {} was killed before execution started, aborting", taskId);
            taskTargetPathMap.remove(taskId);
            return;
        }

        PreCheckService.PreCheckResult preCheckResult = preCheckService.preCheck(taskId, sourcePath, targetPath);
        if (!preCheckResult.isSuccess()) {
            communicator.reportTaskStatus(taskId, "failed", 0, 0, 0, 0, preCheckResult.getMessage());
            return;
        }

        if (killedTaskIds.contains(taskId)) {
            log.info("Task {} was killed during pre-check, aborting", taskId);
            taskTargetPathMap.remove(taskId);
            return;
        }

        // Get source stats before starting
        long[] sourceStats = getSourceStats(sourcePath);
        long totalFiles = sourceStats[0];
        long totalSize = sourceStats[1];
        log.info("Task {} source stats: {} files, {} bytes", taskId, totalFiles, totalSize);

        if (killedTaskIds.contains(taskId)) {
            log.info("Task {} was killed during source stats, aborting", taskId);
            taskTargetPathMap.remove(taskId);
            return;
        }

        List<String> sourceFileList = sourceFileLister.listFiles(sourcePath);
        taskFileListMap.put(taskId, sourceFileList);
        log.info("Task {} listed {} source files", taskId, sourceFileList.size());

        String workDir = agentConfig.getWorkDir() + File.separator + "task_" + taskId;

        TaskProgressDTO progress = new TaskProgressDTO();
        progress.setTaskId(taskId);
        progress.setTotalFiles(totalFiles);
        progress.setTotalSizeBytes(totalSize);
        progress.setStatus("running");
        taskProgressMap.put(taskId, progress);

        // Report initial state with totals
        communicator.reportTaskStatus(taskId, "running", 0, 0, totalFiles, totalSize, null);

        TaskStateStore.TaskMetadata metadata = new TaskStateStore.TaskMetadata();
        metadata.setTaskId(taskId);
        metadata.setSourcePath(sourcePath);
        metadata.setTargetPath(targetPath);
        metadata.setSourceCluster(sourceCluster);
        metadata.setTargetCluster(targetCluster);
        metadata.setDistcpOptions(distcpOptions);
        metadata.setTotalFiles(totalFiles);
        metadata.setTotalSize(totalSize);
        metadata.setSourceFileList(sourceFileList);
        taskStateStore.save(metadata);

        Process process = null;
        Thread watchdog = null;
        try {
            logCollector.replayLogs(taskId);
            String script = scriptGenerator.generateDistcpScript(
                    taskId, sourcePath, targetPath, sourceCluster, targetCluster, distcpOptions, workDir);
            process = processManager.startScript(taskId, script, workDir);
            final Process distcpProcess = process;

            // Start watchdog for timeout (0 = no timeout)
            final int timeoutHours = agentConfig.getTaskTimeoutHours();
            if (timeoutHours > 0) {
                watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(timeoutHours * 3600 * 1000L);
                        if (distcpProcess.isAlive()) {
                            log.warn("Task {} exceeded timeout of {} hours, killing process", taskId, timeoutHours);
                            distcpProcess.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        // Normal: task finished before timeout, watchdog cancelled
                    }
                }, "watchdog-" + taskId);
                watchdog.setDaemon(true);
                watchdog.start();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logCollector.collectLog(taskId, line);
                    parseProgress(taskId, line, progress, totalFiles, totalSize);
                }
            }

            int exitCode = process.waitFor();
            processManager.removeProcess(taskId);

            long completedFiles = progress.getCompletedFiles();
            long completedSizeBytes = progress.getCompletedSizeBytes();

            if (exitCode == 0) {
                // On success: completed = total
                if (totalFiles > 0) {
                    completedFiles = totalFiles;
                    progress.setCompletedFiles(totalFiles);
                }
                if (totalSize > 0) {
                    completedSizeBytes = totalSize;
                    progress.setCompletedSizeBytes(totalSize);
                }
                progress.setProgressPercent(100.0);
                progress.setStatus("success");
                communicator.reportTaskStatus(taskId, "success", completedFiles, completedSizeBytes,
                        totalFiles, totalSize, null);
                retryHandler.clearRetryCount(taskId);
                dataVerifier.verify(taskId, sourcePath, targetPath, sourceFileList);
            } else {
                progress.setStatus("failed");
                String errorMsg = "distcp exit code: " + exitCode;
                communicator.reportTaskStatus(taskId, "failed", completedFiles, completedSizeBytes,
                        totalFiles, totalSize, errorMsg);

                if (agentConfig.isRetryEnabled()) {
                    retryHandler.handleRetry(taskId, sourcePath, targetPath, sourceCluster, targetCluster, distcpOptions);
                }
            }
        } catch (Exception e) {
            log.error("Task execution failed for {}", taskId, e);
            communicator.reportTaskStatus(taskId, "failed", 0, 0,
                    totalFiles, totalSize, e.getMessage());
        } finally {
            if (watchdog != null) {
                watchdog.interrupt();
            }
            lastReportTimeMap.remove(taskId);
            reportLineCountMap.remove(taskId);
            taskProgressMap.remove(taskId);
            taskTargetPathMap.remove(taskId);
            taskSourcePathMap.remove(taskId);
            taskFileListMap.remove(taskId);
            killedTaskIds.remove(taskId);
            logCollector.cleanupTaskLogs(taskId);
            taskStateStore.remove(taskId);
        }
    }

    /**
     * Get source file count and total size using hadoop fs -count / -du
     * Returns [fileCount, totalSizeBytes]
     */
    private long[] getSourceStats(String sourcePath) {
        try {
            String script = scriptGenerator.generateSourceStatScript(sourcePath);
            File workDir = new File(System.getProperty("java.io.tmpdir"), "hdfs-transfer-stats");
            workDir.mkdirs();

            File scriptFile = new File(workDir, "stat.sh");
            try (java.io.FileWriter fw = new java.io.FileWriter(scriptFile)) {
                fw.write(script);
            }
            scriptFile.setExecutable(true);

            ProcessBuilder pb = new ProcessBuilder("/bin/bash", scriptFile.getAbsolutePath());
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            long[] result = new long[]{0, 0};

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("Stat output: {}", line.trim());
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            long v1 = Long.parseLong(parts[0]);
                            long v2 = Long.parseLong(parts[1]);
                            if ( result[0] == 0 && v1 > 0) result[0] = v1;
                            if ( result[1] == 0 && v2 > 0) result[1] = v2;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            process.waitFor();
            scriptFile.delete();
            return result;
        } catch (Exception e) {
            log.warn("Failed to get source stats for {}, using zeros", sourcePath, e);
            return new long[]{0, 0};
        }
    }

    private void parseProgress(Long taskId, String line, TaskProgressDTO progress,
                               long totalFiles, long totalSize) {
        if (line == null || line.isEmpty()) return;

        // 1. MapReduce progress: "map 75% reduce 0%"
        Matcher mrMatcher = MR_PROGRESS.matcher(line);
        if (mrMatcher.find()) {
            try {
                int mapPct = Integer.parseInt(mrMatcher.group(1));
                progress.setProgressPercent(mapPct);
                if (totalFiles > 0) {
                    progress.setCompletedFiles((long) (totalFiles * mapPct / 100.0));
                }
                if (totalSize > 0) {
                    progress.setCompletedSizeBytes((long) (totalSize * mapPct / 100.0));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. "Copied N files"
        Matcher cpMatcher = FILES_COPIED.matcher(line);
        if (cpMatcher.find()) {
            try {
                long files = Long.parseLong(cpMatcher.group(1));
                progress.setCompletedFiles(files);
            } catch (NumberFormatException ignored) {
            }
        }

        // 3. Generic percent lines
        Matcher pctMatcher = GENERIC_PERCENT.matcher(line);
        if (pctMatcher.find() && !mrMatcher.find()) {
            try {
                double pct = Double.parseDouble(pctMatcher.group(1));
                progress.setProgressPercent(pct);
                if (totalFiles > 0) {
                    progress.setCompletedFiles((long) (totalFiles * pct / 100.0));
                }
                if (totalSize > 0) {
                    progress.setCompletedSizeBytes((long) (totalSize * pct / 100.0));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        progress.setCurrentFile(line);
        progress.setStatus("running");

        // Throttled progress report
        reportThrottled(taskId, progress, totalFiles, totalSize);
    }

    private void reportThrottled(Long taskId, TaskProgressDTO progress, long totalFiles, long totalSize) {
        int cnt = reportLineCountMap.getOrDefault(taskId, 0) + 1;
        reportLineCountMap.put(taskId, cnt);
        long now = System.currentTimeMillis();
        long last = lastReportTimeMap.getOrDefault(taskId, 0L);
        if (cnt % 20 == 0 || (now - last > 10000)) {
            lastReportTimeMap.put(taskId, now);
            communicator.reportTaskStatus(taskId, "running",
                    progress.getCompletedFiles(),
                    progress.getCompletedSizeBytes(),
                    totalFiles, totalSize, null);
        }
    }

    public void killTask(Long taskId) {
        processManager.killProcess(taskId);
        taskProgressMap.remove(taskId);
        taskTargetPathMap.remove(taskId);
    }

    public boolean stopTask(Long taskId) {
        Process process = processManager.getProcess(taskId);
        if (process == null || !process.isAlive()) {
            log.warn("StopTask: task {} process not found or already dead", taskId);
            return false;
        }
        process.destroy();
        log.info("Task {} stop signal sent (graceful)", taskId);
        return true;
    }

    public boolean forceKillTask(Long taskId) {
        killedTaskIds.add(taskId);
        processManager.killProcess(taskId);
        killYarnApplication(taskId);
        taskProgressMap.remove(taskId);

        String targetPath = taskTargetPathMap.remove(taskId);
        String sourcePath = taskSourcePathMap.remove(taskId);
        List<String> fileList = taskFileListMap.remove(taskId);
        if (targetPath != null && !targetPath.isEmpty() && sourcePath != null && !sourcePath.isEmpty()) {
            cleanupTargetPath(taskId, sourcePath, targetPath, fileList);
        } else {
            log.warn("ForceKill: task {} sourcePath or targetPath unknown, skipping cleanup", taskId);
        }
        return true;
    }

    private void killYarnApplication(Long taskId) {
        String appId = findYarnApplicationId(taskId);
        if (appId == null) {
            log.info("Task {} no YARN application found, skipping yarn kill", taskId);
            return;
        }
        try {
            String yarnBin = agentConfig.getYarnBin();
            log.info("Task {} killing YARN application: {}", taskId, appId);
            ProcessBuilder pb = new ProcessBuilder(yarnBin, "application", "-kill", appId);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("Task {} yarn kill: {}", taskId, line);
                    logCollector.collectLog(taskId, "[KILL] " + line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Task {} YARN application {} killed successfully", taskId, appId);
            } else {
                log.warn("Task {} YARN application kill exited with code {}", taskId, exitCode);
            }
        } catch (Exception e) {
            log.error("Task {} failed to kill YARN application", taskId, e);
        }
    }

    private String findYarnApplicationId(Long taskId) {
        try {
            String yarnBin = agentConfig.getYarnBin();
            String jobName = "hdfs-transfer-task-" + taskId;
            ProcessBuilder pb = new ProcessBuilder(yarnBin, "application", "-list");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(jobName)) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 0) {
                            return parts[0];
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("Task {} failed to find YARN application", taskId, e);
        }
        return null;
    }

    private void cleanupTargetPath(Long taskId, String sourcePath, String targetPath, List<String> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            log.warn("Task {} no file list available, skipping cleanup", taskId);
            return;
        }

        String hadoopBin = agentConfig.getHadoopHome() + "/bin/hadoop";
        String tgt = SourceFileLister.normalizeTargetPath(targetPath);
        log.info("Task {} cleanup: removing {} files from target", taskId, fileList.size());

        java.util.Set<String> targetDirs = new java.util.TreeSet<>((a, b) -> Integer.compare(b.length(), a.length()));

        for (String sourceFilePath : fileList) {
            String targetFilePath = SourceFileLister.getTargetFilePath(sourcePath, targetPath, sourceFilePath);
            try {
                ProcessBuilder pb = new ProcessBuilder(hadoopBin, "fs", "-rm", "-r", "-skipTrash", targetFilePath);
                pb.redirectErrorStream(true);
                Process rmProcess = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(rmProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("Task {} cleanup: {} - {}", taskId, targetFilePath, line);
                        logCollector.collectLog(taskId, "[CLEANUP] " + targetFilePath + " " + line);
                    }
                }
                rmProcess.waitFor();

                int lastSlash = targetFilePath.lastIndexOf('/');
                if (lastSlash > 0) {
                    String parentDir = targetFilePath.substring(0, lastSlash);
                    if (parentDir.length() > tgt.length()) {
                        targetDirs.add(parentDir);
                    }
                }
            } catch (Exception e) {
                log.error("Task {} cleanup failed for file: {}", taskId, targetFilePath, e);
            }
        }

        for (String dir : targetDirs) {
            try {
                ProcessBuilder pb = new ProcessBuilder(hadoopBin, "fs", "-rm", "-r", "-skipTrash", dir);
                pb.redirectErrorStream(true);
                Process rmProcess = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(rmProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("Task {} cleanup dir: {} - {}", taskId, dir, line);
                    }
                }
                rmProcess.waitFor();
            } catch (Exception e) {
                log.debug("Task {} rmdir {} failed", taskId, dir);
            }
        }

        log.info("Task {} cleanup completed", taskId);
    }

    public String getCurrentStatus() {
        int running = taskProgressMap.size();
        if (running >= agentConfig.getMaxParallelTasks()) {
            return "busy";
        }
        return running > 0 ? "running" : "online";
    }

    public int getRunningTaskCount() {
        return taskProgressMap.size();
    }

    public List<TaskProgressDTO> getAllTaskProgress() {
        return new ArrayList<>(taskProgressMap.values());
    }
}
