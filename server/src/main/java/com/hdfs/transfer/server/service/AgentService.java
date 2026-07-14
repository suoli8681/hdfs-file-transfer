package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.common.dto.HeartbeatDTO;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.mapper.AgentNodeMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentService {

    private final AgentNodeMapper agentNodeMapper;

    public AgentService(AgentNodeMapper agentNodeMapper) {
        this.agentNodeMapper = agentNodeMapper;
    }

    public List<AgentNodeEntity> listAll() {
        return agentNodeMapper.selectList(new LambdaQueryWrapper<AgentNodeEntity>()
                .orderByDesc(AgentNodeEntity::getLastHeartbeatTime));
    }

    public AgentNodeEntity getByAgentId(String agentId) {
        return agentNodeMapper.selectOne(
                new LambdaQueryWrapper<AgentNodeEntity>().eq(AgentNodeEntity::getAgentId, agentId));
    }

    public void register(AgentNodeEntity entity) {
        AgentNodeEntity exist = getByAgentId(entity.getAgentId());
        if (exist != null) {
            entity.setId(exist.getId());
            entity.setLastHeartbeatTime(LocalDateTime.now());
            agentNodeMapper.updateById(entity);
        } else {
            entity.setStatus("online");
            entity.setLastHeartbeatTime(LocalDateTime.now());
            agentNodeMapper.insert(entity);
        }
    }

    public void processHeartbeat(HeartbeatDTO dto) {
        AgentNodeEntity entity = getByAgentId(dto.getAgentId());
        if (entity != null) {
            entity.setStatus(dto.getStatus());
            entity.setRunningTaskCount(dto.getRunningTaskCount());
            entity.setMaxParallelTasks(dto.getMaxParallelTasks());
            entity.setCpuUsage(dto.getCpuUsage());
            entity.setMemoryUsage(dto.getMemoryUsage());
            entity.setLastHeartbeatTime(LocalDateTime.now());
            agentNodeMapper.updateById(entity);
        } else {
            AgentNodeEntity newEntity = new AgentNodeEntity();
            newEntity.setAgentId(dto.getAgentId());
            newEntity.setAgentHost(dto.getAgentHost());
            newEntity.setStatus(dto.getStatus());
            newEntity.setRunningTaskCount(dto.getRunningTaskCount());
            newEntity.setMaxParallelTasks(dto.getMaxParallelTasks());
            newEntity.setCpuUsage(dto.getCpuUsage());
            newEntity.setMemoryUsage(dto.getMemoryUsage());
            newEntity.setLastHeartbeatTime(LocalDateTime.now());
            agentNodeMapper.insert(newEntity);
        }
    }

    public void markOffline(String agentId) {
        AgentNodeEntity entity = getByAgentId(agentId);
        if (entity != null) {
            entity.setStatus("offline");
            entity.setRunningTaskCount(0);
            agentNodeMapper.updateById(entity);
        }
    }
}
