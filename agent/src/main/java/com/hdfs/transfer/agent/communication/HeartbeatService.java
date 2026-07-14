package com.hdfs.transfer.agent.communication;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.executor.TaskExecutionManager;
import com.hdfs.transfer.common.dto.HeartbeatDTO;
import com.hdfs.transfer.common.dto.TaskProgressDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;

@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final AgentConfig agentConfig;
    private final ServerCommunicator communicator;
    private final TaskExecutionManager executionManager;

    public HeartbeatService(AgentConfig agentConfig, ServerCommunicator communicator,
                            TaskExecutionManager executionManager) {
        this.agentConfig = agentConfig;
        this.communicator = communicator;
        this.executionManager = executionManager;
    }

    @Scheduled(fixedDelayString = "${hdfs.transfer.server.heartbeat-interval:10}000")
    public void sendHeartbeat() {
        try {
            HeartbeatDTO dto = new HeartbeatDTO();
            dto.setAgentId(agentConfig.getAgentId());
            dto.setAgentHost(agentConfig.getAgentHost());
            dto.setStatus(executionManager.getCurrentStatus());
            dto.setRunningTaskCount(executionManager.getRunningTaskCount());
            dto.setMaxParallelTasks(agentConfig.getMaxParallelTasks());
            dto.setCpuUsage(getCpuUsage());
            dto.setMemoryUsage(getMemoryUsage());
            dto.setTaskProgressList(executionManager.getAllTaskProgress());
            dto.setTimestamp(System.currentTimeMillis());
            communicator.sendHeartbeat(dto);
        } catch (Exception e) {
            log.error("Heartbeat error", e);
        }
    }

    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                double cpu = ((com.sun.management.OperatingSystemMXBean) osBean).getSystemCpuLoad();
                return cpu < 0 ? 0 : cpu * 100;
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    private double getMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long total = runtime.totalMemory();
            long free = runtime.freeMemory();
            return (total - free) * 100.0 / total;
        } catch (Exception e) {
            return 0;
        }
    }
}