package com.hdfs.transfer.server.service;

import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.mapper.TaskInstanceMapper;
import com.hdfs.transfer.server.mapper.AgentNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TaskDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TaskInstanceMapper instanceMapper;
    private final AgentNodeMapper agentNodeMapper;

    @Value("${hdfs.transfer.max-concurrent-tasks:10}")
    private int maxConcurrentTasks;

    @Value("${hdfs.transfer.dispatch-timeout-seconds:120}")
    private int dispatchTimeoutSeconds;

    public TaskDispatchService(TaskInstanceMapper instanceMapper, AgentNodeMapper agentNodeMapper) {
        this.instanceMapper = instanceMapper;
        this.agentNodeMapper = agentNodeMapper;
    }

    @Transactional
    public void dispatchPendingTasks() {
        recoverStuckDispatchingTasks();

        List<TaskInstanceEntity> pendingInstances = instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .in(TaskInstanceEntity::getStatus, "pending", "retrying")
                        .orderByAsc(TaskInstanceEntity::getPriority)
                        .last("LIMIT 20"));

        for (TaskInstanceEntity instance : pendingInstances) {
            if (!canDispatch(instance)) {
                continue;
            }
            instance.setStatus("dispatching");
            instance.setLastExecTime(LocalDateTime.now().format(DTF));
            instanceMapper.updateById(instance);
            log.info("Dispatched instance: {} to agent: {}", instance.getInstanceName(), instance.getAgentId());
        }
    }

    private void recoverStuckDispatchingTasks() {
        List<TaskInstanceEntity> dispatchingInstances = instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .eq(TaskInstanceEntity::getStatus, "dispatching"));
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(dispatchTimeoutSeconds);
        for (TaskInstanceEntity instance : dispatchingInstances) {
            if (instance.getLastExecTime() == null) {
                instance.setStatus("pending");
                instanceMapper.updateById(instance);
                log.warn("Instance {} had no lastExecTime, reset to pending", instance.getInstanceName());
                continue;
            }
            try {
                LocalDateTime execTime = LocalDateTime.parse(instance.getLastExecTime(), DTF);
                if (execTime.isBefore(cutoff)) {
                    instance.setStatus("pending");
                    instanceMapper.updateById(instance);
                    log.warn("Instance {} stuck in dispatching for over {}s, reset to pending",
                            instance.getInstanceName(), dispatchTimeoutSeconds);
                }
            } catch (Exception e) {
                log.warn("Instance {} has unparseable lastExecTime '{}', reset to pending",
                        instance.getInstanceName(), instance.getLastExecTime());
                instance.setStatus("pending");
                instanceMapper.updateById(instance);
            }
        }
    }

    private boolean canDispatch(TaskInstanceEntity instance) {
        if (instance.getAgentId() == null || instance.getAgentId().isEmpty()) {
            log.warn("Instance {} has no agent assigned", instance.getInstanceName());
            return false;
        }
        AgentNodeEntity agent = agentNodeMapper.selectOne(
                new LambdaQueryWrapper<AgentNodeEntity>().eq(AgentNodeEntity::getAgentId, instance.getAgentId()));
        if (agent == null || "offline".equals(agent.getStatus())) {
            instance.setErrorMsg("Agent offline");
            instance.setStatus("failed");
            instance.setCompleteTime(LocalDateTime.now().format(DTF));
            instanceMapper.updateById(instance);
            return false;
        }
        if (agent.getRunningTaskCount() != null && agent.getMaxParallelTasks() != null
                && agent.getRunningTaskCount() >= agent.getMaxParallelTasks()) {
            log.warn("Agent {} is busy, skip instance {}", instance.getAgentId(), instance.getInstanceName());
            return false;
        }
        return true;
    }
}
