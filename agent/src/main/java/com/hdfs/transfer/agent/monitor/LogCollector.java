package com.hdfs.transfer.agent.monitor;

import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.common.dto.LogEntryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class LogCollector {

    private static final Logger log = LoggerFactory.getLogger(LogCollector.class);

    private final AgentConfig agentConfig;
    private final ServerCommunicator communicator;
    private final Queue<LogEntryDTO> logQueue = new ConcurrentLinkedQueue<>();
    private final Set<Long> reportedTaskIds = ConcurrentHashMap.newKeySet();

    public LogCollector(AgentConfig agentConfig, ServerCommunicator communicator) {
        this.agentConfig = agentConfig;
        this.communicator = communicator;
        ensureLogDir();
    }

    private void ensureLogDir() {
        try {
            Path logDir = Paths.get(agentConfig.getWorkDir(), "logs");
            Files.createDirectories(logDir);
        } catch (IOException e) {
            log.error("Failed to create log directory", e);
        }
    }

    private Path getLogFilePath(Long taskId) {
        return Paths.get(agentConfig.getWorkDir(), "logs", "task-" + taskId + ".log");
    }

    private Path getReportedFilePath(Long taskId) {
        return Paths.get(agentConfig.getWorkDir(), "logs", "task-" + taskId + ".reported");
    }

    public void collectLog(Long taskId, String line) {
        if (line == null || line.trim().isEmpty()) return;

        LogEntryDTO entry = new LogEntryDTO();
        entry.setTaskId(taskId);
        entry.setTimestamp(System.currentTimeMillis());

        if (line.contains("[ERROR]") || line.contains("Error") || line.contains("Exception")) {
            entry.setLevel("ERROR");
        } else if (line.contains("[WARN]") || line.contains("WARN")) {
            entry.setLevel("WARN");
        } else {
            entry.setLevel("INFO");
        }
        entry.setContent(line);
        logQueue.offer(entry);

        writeToLocalFile(taskId, entry);
    }

    private void writeToLocalFile(Long taskId, LogEntryDTO entry) {
        try {
            String record = entry.getTimestamp() + "|" + entry.getLevel() + "|" + entry.getContent() + "\n";
            Files.write(getLogFilePath(taskId), record.getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write log file for task {}", taskId, e);
        }
    }

    @Scheduled(fixedDelayString = "${hdfs.transfer.agent.log-collect-interval:5}000")
    public void flushLogs() {
        if (logQueue.isEmpty()) return;

        List<LogEntryDTO> batch = new ArrayList<>();
        int batchSize = agentConfig.getLogBatchSize();
        while (!logQueue.isEmpty() && batch.size() < batchSize) {
            batch.add(logQueue.poll());
        }

        if (!batch.isEmpty()) {
            communicator.uploadLogs(batch);
        }
    }

    public void replayLogs(Long taskId) {
        Path logFile = getLogFilePath(taskId);
        Path reportedFile = getReportedFilePath(taskId);

        if (!Files.exists(logFile)) {
            return;
        }

        long reportedOffset = 0;
        if (Files.exists(reportedFile)) {
            try {
                String content = new String(Files.readAllBytes(reportedFile), "UTF-8").trim();
                if (!content.isEmpty()) {
                    reportedOffset = Long.parseLong(content);
                }
            } catch (Exception e) {
                log.warn("Failed to read reported offset for task {}", taskId, e);
            }
        }

        try {
            List<String> lines = Files.readAllLines(logFile, java.nio.charset.StandardCharsets.UTF_8);
            List<LogEntryDTO> batch = new ArrayList<>();
            long currentOffset = 0;

            for (String line : lines) {
                if (currentOffset < reportedOffset) {
                    currentOffset += line.getBytes("UTF-8").length + 1;
                    continue;
                }

                LogEntryDTO entry = parseLocalLogLine(taskId, line);
                if (entry != null) {
                    batch.add(entry);
                    currentOffset += line.getBytes("UTF-8").length + 1;
                }
            }

            if (!batch.isEmpty()) {
                for (int i = 0; i < batch.size(); i += agentConfig.getLogBatchSize()) {
                    int end = Math.min(i + agentConfig.getLogBatchSize(), batch.size());
                    communicator.uploadLogs(batch.subList(i, end));
                }
                log.info("Replayed {} log entries for task {}", batch.size(), taskId);
            }

            try {
                Files.write(reportedFile, String.valueOf(currentOffset).getBytes("UTF-8"),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                log.error("Failed to update reported offset for task {}", taskId, e);
            }

        } catch (IOException e) {
            log.error("Failed to replay logs for task {}", taskId, e);
        }
    }

    private LogEntryDTO parseLocalLogLine(Long taskId, String line) {
        if (line == null || line.isEmpty()) return null;
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return null;

        LogEntryDTO entry = new LogEntryDTO();
        entry.setTaskId(taskId);
        try {
            entry.setTimestamp(Long.parseLong(parts[0]));
        } catch (NumberFormatException e) {
            entry.setTimestamp(System.currentTimeMillis());
        }
        entry.setLevel(parts[1]);
        entry.setContent(parts[2]);
        return entry;
    }

    public void cleanupTaskLogs(Long taskId) {
        try {
            Files.deleteIfExists(getLogFilePath(taskId));
            Files.deleteIfExists(getReportedFilePath(taskId));
            log.info("Cleaned up local log files for task {}", taskId);
        } catch (IOException e) {
            log.warn("Failed to cleanup log files for task {}", taskId, e);
        }
    }
}
