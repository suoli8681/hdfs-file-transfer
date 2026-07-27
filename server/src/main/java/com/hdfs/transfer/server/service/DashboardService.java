package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.mapper.TaskInstanceMapper;
import com.hdfs.transfer.server.mapper.AgentNodeMapper;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final TaskInstanceMapper instanceMapper;
    private final AgentNodeMapper agentNodeMapper;

    public DashboardService(TaskInstanceMapper instanceMapper, AgentNodeMapper agentNodeMapper) {
        this.instanceMapper = instanceMapper;
        this.agentNodeMapper = agentNodeMapper;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new HashMap<>();

        List<TaskInstanceEntity> allInstances = instanceMapper.selectList(null);
        long totalInstances = allInstances.size();
        long pendingTasks = allInstances.stream().filter(t -> "pending".equals(t.getStatus()) || "dispatching".equals(t.getStatus())).count();
        long runningTasks = allInstances.stream().filter(t -> "running".equals(t.getStatus()) || "retrying".equals(t.getStatus())).count();
        long successTasks = allInstances.stream().filter(t -> "success".equals(t.getStatus())).count();
        long failedTasks = allInstances.stream().filter(t -> "failed".equals(t.getStatus())).count();
        long stoppedTasks = allInstances.stream().filter(t -> "stopped".equals(t.getStatus())).count();
        long killedTasks = allInstances.stream().filter(t -> "killed".equals(t.getStatus())).count();

        long totalTransferred = allInstances.stream()
                .filter(t -> t.getCompletedSize() != null)
                .mapToLong(TaskInstanceEntity::getCompletedSize)
                .sum();

        List<AgentNodeEntity> agents = agentNodeMapper.selectList(null);
        long onlineAgents = agents.stream().filter(a -> "online".equals(a.getStatus())).count();

        result.put("totalTasks", totalInstances);
        result.put("pendingTasks", pendingTasks);
        result.put("runningTasks", runningTasks);
        result.put("successTasks", successTasks);
        result.put("failedTasks", failedTasks);
        result.put("stoppedTasks", stoppedTasks);
        result.put("killedTasks", killedTasks);
        result.put("totalTransferredBytes", totalTransferred);
        result.put("totalAgents", agents.size());
        result.put("onlineAgents", onlineAgents);
        return result;
    }

    public List<TaskInstanceEntity> getRecentTasks(int limit) {
        return instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .orderByDesc(TaskInstanceEntity::getCreateTime)
                        .last("LIMIT " + limit));
    }
}
