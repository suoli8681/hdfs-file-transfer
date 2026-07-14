package com.hdfs.transfer.agent.monitor;

import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.common.dto.LogEntryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Component
public class LogCollector {

    private static final Logger log = LoggerFactory.getLogger(LogCollector.class);

    private final AgentConfig agentConfig;
    private final ServerCommunicator communicator;
    private final Queue<LogEntryDTO> logQueue = new ConcurrentLinkedQueue<>();

    public LogCollector(AgentConfig agentConfig, ServerCommunicator communicator) {
        this.agentConfig = agentConfig;
        this.communicator = communicator;
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
}