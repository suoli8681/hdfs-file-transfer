package com.hdfs.transfer.server.scheduler;

import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AgentMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(AgentMonitorJob.class);

    private final AgentService agentService;

    @Value("${hdfs.transfer.agent-heartbeat-timeout:60}")
    private int heartbeatTimeout;

    public AgentMonitorJob(AgentService agentService) {
        this.agentService = agentService;
    }

    @Scheduled(fixedDelay = 30000)
    public void checkAgentStatus() {
        List<AgentNodeEntity> agents = agentService.listAll();
        LocalDateTime now = LocalDateTime.now();
        for (AgentNodeEntity agent : agents) {
            if (agent.getLastHeartbeatTime() == null) continue;
            if (agent.getLastHeartbeatTime().plusSeconds(heartbeatTimeout).isBefore(now)) {
                if (!"offline".equals(agent.getStatus())) {
                    log.warn("Agent {} heartbeat timeout, mark offline", agent.getAgentId());
                    agentService.markOffline(agent.getAgentId());
                }
            }
        }
    }
}