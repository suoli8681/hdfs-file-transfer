package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.mapper.MigrationTaskMapper;
import com.hdfs.transfer.server.mapper.AgentNodeMapper;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final MigrationTaskMapper taskMapper;
    private final AgentNodeMapper agentNodeMapper;

    public DashboardService(MigrationTaskMapper taskMapper, AgentNodeMapper agentNodeMapper) {
        this.taskMapper = taskMapper;
        this.agentNodeMapper = agentNodeMapper;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new HashMap<>();

        List<MigrationTaskEntity> allTasks = taskMapper.selectList(null);
        long totalTasks = allTasks.size();
        long runningTasks = allTasks.stream().filter(t -> "running".equals(t.getStatus())).count();
        long successTasks = allTasks.stream().filter(t -> "success".equals(t.getStatus())).count();
        long failedTasks = allTasks.stream().filter(t -> "failed".equals(t.getStatus())).count();
        long stoppedTasks = allTasks.stream().filter(t -> "stopped".equals(t.getStatus())).count();
        long killedTasks = allTasks.stream().filter(t -> "killed".equals(t.getStatus())).count();

        long totalTransferred = allTasks.stream()
                .filter(t -> t.getCompletedSize() != null)
                .mapToLong(MigrationTaskEntity::getCompletedSize)
                .sum();

        List<AgentNodeEntity> agents = agentNodeMapper.selectList(null);
        long onlineAgents = agents.stream().filter(a -> "online".equals(a.getStatus())).count();

        result.put("totalTasks", totalTasks);
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

    public List<MigrationTaskEntity> getRecentTasks(int limit) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<MigrationTaskEntity>()
                        .orderByDesc(MigrationTaskEntity::getCreateTime)
                        .last("LIMIT " + limit));
    }
}
