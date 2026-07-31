package com.hdfs.transfer.agent.recovery;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.executor.TaskStateStore;
import com.hdfs.transfer.agent.verify.DataVerifier;
import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.monitor.LogCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@Component
public class TaskRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryService.class);

    private final AgentConfig agentConfig;
    private final TaskStateStore taskStateStore;
    private final DataVerifier dataVerifier;
    private final ServerCommunicator communicator;
    private final LogCollector logCollector;

    public TaskRecoveryService(AgentConfig agentConfig, TaskStateStore taskStateStore,
                                DataVerifier dataVerifier, ServerCommunicator communicator,
                                LogCollector logCollector) {
        this.agentConfig = agentConfig;
        this.taskStateStore = taskStateStore;
        this.dataVerifier = dataVerifier;
        this.communicator = communicator;
        this.logCollector = logCollector;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<TaskStateStore.TaskMetadata> unfinished = taskStateStore.loadUnfinished();
        if (unfinished.isEmpty()) {
            log.info("No unfinished tasks to recover");
            return;
        }

        log.info("Found {} unfinished tasks, starting recovery", unfinished.size());

        for (TaskStateStore.TaskMetadata metadata : unfinished) {
            recoverTask(metadata);
        }
    }

    private void recoverTask(TaskStateStore.TaskMetadata metadata) {
        Long taskId = metadata.getTaskId();
        log.info("Recovering task {}", taskId);

        try {
            String yarnState = checkYarnApplicationState(taskId);
            log.info("Task {} YARN application state: {}", taskId, yarnState);

            if ("RUNNING".equals(yarnState) || "ACCEPTED".equals(yarnState)) {
                log.info("Task {} YARN job still running, waiting for completion", taskId);
                waitForYarnCompletion(metadata);
            } else if ("FINISHED".equals(yarnState) || "SUCCEEDED".equals(yarnState)) {
                log.info("Task {} YARN job already finished, verifying data", taskId);
                finishTaskSuccess(taskId, metadata);
            } else if ("FAILED".equals(yarnState) || "KILLED".equals(yarnState)) {
                log.warn("Task {} YARN job was {} , reporting failure", taskId, yarnState);
                finishTaskFailed(taskId, metadata, "YARN job " + yarnState);
            } else {
                log.info("Task {} YARN job not found, attempting data verification", taskId);
                finishTaskSuccess(taskId, metadata);
            }
        } catch (Exception e) {
            log.error("Failed to recover task {}", taskId, e);
            finishTaskFailed(taskId, metadata, "Recovery failed: " + e.getMessage());
        }
    }

    private String checkYarnApplicationState(Long taskId) {
        try {
            String hadoopBin = agentConfig.getYarnBin();
            String jobName = "hdfs-transfer-task-" + taskId;

            ProcessBuilder pb = new ProcessBuilder(hadoopBin, "application", "-list");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(jobName)) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 5) {
                            return parts[parts.length - 1].trim();
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("Failed to check YARN application state for task {}", taskId, e);
        }
        return "NOT_FOUND";
    }

    private void waitForYarnCompletion(TaskStateStore.TaskMetadata metadata) {
        Long taskId = metadata.getTaskId();
        String sourcePath = metadata.getSourcePath();
        String targetPath = metadata.getTargetPath();

        Thread waiter = new Thread(() -> {
            int maxRetries = 720;
            int interval = 30;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    Thread.sleep(interval * 1000L);
                } catch (InterruptedException e) {
                    return;
                }

                String state = checkYarnApplicationState(taskId);
                log.info("Task {} YARN check {}/{}: state={}", taskId, i + 1, maxRetries, state);

                if ("RUNNING".equals(state) || "ACCEPTED".equals(state)) {
                    communicator.reportTaskStatus(taskId, "running",
                            0, 0, metadata.getTotalFiles(), metadata.getTotalSize(), null);
                    continue;
                }

                if ("FINISHED".equals(state) || "SUCCEEDED".equals(state)) {
                    finishTaskSuccess(taskId, metadata);
                    return;
                }

                if ("FAILED".equals(state) || "KILLED".equals(state)) {
                    finishTaskFailed(taskId, metadata, "YARN job " + state);
                    return;
                }

                if ("NOT_FOUND".equals(state)) {
                    log.info("Task {} YARN job disappeared, attempting verification", taskId);
                    finishTaskSuccess(taskId, metadata);
                    return;
                }
            }

            log.warn("Task {} YARN job still running after {} retries, giving up recovery", taskId, maxRetries);
            finishTaskFailed(taskId, metadata, "YARN job did not complete within timeout");
        }, "yarn-wait-" + taskId);
        waiter.setDaemon(true);
        waiter.start();
    }

    private void finishTaskSuccess(Long taskId, TaskStateStore.TaskMetadata metadata) {
        logCollector.replayLogs(taskId);

        String sourcePath = metadata.getSourcePath();
        String targetPath = metadata.getTargetPath();
        long totalFiles = metadata.getTotalFiles();
        long totalSize = metadata.getTotalSize();

        communicator.reportTaskStatus(taskId, "success", totalFiles, totalSize, totalFiles, totalSize, null);

        dataVerifier.verify(taskId, sourcePath, targetPath, metadata.getSourceFileList());

        taskStateStore.remove(taskId);
        logCollector.cleanupTaskLogs(taskId);
        log.info("Task {} recovery completed (success)", taskId);
    }

    private void finishTaskFailed(Long taskId, TaskStateStore.TaskMetadata metadata, String reason) {
        logCollector.replayLogs(taskId);

        communicator.reportTaskStatus(taskId, "failed", 0, 0,
                metadata.getTotalFiles(), metadata.getTotalSize(), reason);

        taskStateStore.remove(taskId);
        logCollector.cleanupTaskLogs(taskId);
        log.info("Task {} recovery completed (failed: {})", taskId, reason);
    }
}
