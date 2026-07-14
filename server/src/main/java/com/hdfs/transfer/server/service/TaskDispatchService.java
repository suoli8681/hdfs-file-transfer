package com.hdfs.transfer.server.service;

import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.mapper.MigrationTaskMapper;
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

    private final MigrationTaskMapper taskMapper;
    private final AgentNodeMapper agentNodeMapper;

    @Value("${hdfs.transfer.max-concurrent-tasks:10}")
    private int maxConcurrentTasks;

    @Value("${hdfs.transfer.dispatch-timeout-seconds:120}")
    private int dispatchTimeoutSeconds;

    public TaskDispatchService(MigrationTaskMapper taskMapper, AgentNodeMapper agentNodeMapper) {
        this.taskMapper = taskMapper;
        this.agentNodeMapper = agentNodeMapper;
    }

    @Transactional
    public void dispatchPendingTasks() {
        recoverStuckDispatchingTasks();

        List<MigrationTaskEntity> pendingTasks = taskMapper.selectList(
                new LambdaQueryWrapper<MigrationTaskEntity>()
                        .in(MigrationTaskEntity::getStatus, "pending", "retrying")
                        .orderByAsc(MigrationTaskEntity::getPriority)
                        .last("LIMIT 20"));

        for (MigrationTaskEntity task : pendingTasks) {
            if (!canDispatch(task)) {
                continue;
            }
            task.setStatus("dispatching");
            task.setLastExecTime(LocalDateTime.now().format(DTF));
            taskMapper.updateById(task);
            log.info("Dispatched task: {} to agent: {}", task.getTaskName(), task.getAgentId());
        }
    }

    private void recoverStuckDispatchingTasks() {
        List<MigrationTaskEntity> dispatchingTasks = taskMapper.selectList(
                new LambdaQueryWrapper<MigrationTaskEntity>()
                        .eq(MigrationTaskEntity::getStatus, "dispatching"));
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(dispatchTimeoutSeconds);
        for (MigrationTaskEntity task : dispatchingTasks) {
            if (task.getLastExecTime() == null) {
                task.setStatus("pending");
                taskMapper.updateById(task);
                log.warn("Task {} had no lastExecTime, reset to pending", task.getTaskName());
                continue;
            }
            try {
                LocalDateTime execTime = LocalDateTime.parse(task.getLastExecTime(), DTF);
                if (execTime.isBefore(cutoff)) {
                    task.setStatus("pending");
                    taskMapper.updateById(task);
                    log.warn("Task {} stuck in dispatching for over {}s, reset to pending",
                            task.getTaskName(), dispatchTimeoutSeconds);
                }
            } catch (Exception e) {
                log.warn("Task {} has unparseable lastExecTime '{}', reset to pending",
                        task.getTaskName(), task.getLastExecTime());
                task.setStatus("pending");
                taskMapper.updateById(task);
            }
        }
    }

    private boolean canDispatch(MigrationTaskEntity task) {
        if (task.getAgentId() == null || task.getAgentId().isEmpty()) {
            log.warn("Task {} has no agent assigned", task.getTaskName());
            return false;
        }
        AgentNodeEntity agent = agentNodeMapper.selectOne(
                new LambdaQueryWrapper<AgentNodeEntity>().eq(AgentNodeEntity::getAgentId, task.getAgentId()));
        if (agent == null || "offline".equals(agent.getStatus())) {
            task.setErrorMsg("Agent offline");
            task.setStatus("failed");
            taskMapper.updateById(task);
            return false;
        }
        if (agent.getRunningTaskCount() != null && agent.getMaxParallelTasks() != null
                && agent.getRunningTaskCount() >= agent.getMaxParallelTasks()) {
            log.warn("Agent {} is busy, skip task {}", task.getAgentId(), task.getTaskName());
            return false;
        }
        return true;
    }

    public List<MigrationTaskEntity> getTasksByTimeRange(String startTime, String endTime) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<MigrationTaskEntity>()
                        .ge(MigrationTaskEntity::getCreateTime, LocalDateTime.parse(startTime, DTF))
                        .le(MigrationTaskEntity::getCreateTime, LocalDateTime.parse(endTime, DTF)));
    }

}
