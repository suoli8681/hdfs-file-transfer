package com.hdfs.transfer.agent.retry;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.executor.TaskExecutionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private final AgentConfig agentConfig;
    private final TaskExecutionManager executionManager;
    private final ServerCommunicator communicator;
    private final Map<Long, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();

    public RetryHandler(AgentConfig agentConfig, @Lazy TaskExecutionManager executionManager,
                        ServerCommunicator communicator) {
        this.agentConfig = agentConfig;
        this.executionManager = executionManager;
        this.communicator = communicator;
    }

    public void handleRetry(Long taskId, String sourcePath, String targetPath,
                            String sourceCluster, String targetCluster,
                            String distcpOptions) {
        AtomicInteger retryCount = retryCountMap.computeIfAbsent(taskId, k -> new AtomicInteger(0));
        int currentCount = retryCount.incrementAndGet();

        if (currentCount > agentConfig.getRetryMaxCount()) {
            log.warn("Task {} exceeded max retry count {}, marking as failed", taskId, agentConfig.getRetryMaxCount());
            communicator.reportTaskStatus(taskId, "failed", 0, 0, 0, 0, "Max retries exceeded");
            retryCountMap.remove(taskId);
            return;
        }

        String updatedOptions = distcpOptions;
        if (updatedOptions == null) {
            updatedOptions = "";
        }
        if (!updatedOptions.contains("-update")) {
            updatedOptions = "-update " + updatedOptions;
        }

        log.info("Retrying task {} (attempt {}/{}) with -update flag", taskId, currentCount, agentConfig.getRetryMaxCount());
        communicator.reportTaskStatus(taskId, "retrying", 0, 0, 0, 0,
                "Retry attempt " + currentCount + "/" + agentConfig.getRetryMaxCount());

        try {
            Thread.sleep(5000L * currentCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executionManager.executeTask(taskId, sourcePath, targetPath, sourceCluster, targetCluster, updatedOptions);
    }

    public void clearRetryCount(Long taskId) {
        retryCountMap.remove(taskId);
    }
}