package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.common.dto.LogEntryDTO;
import com.hdfs.transfer.server.entity.TaskLogEntity;
import com.hdfs.transfer.server.mapper.TaskLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskLogService {

    private final TaskLogMapper taskLogMapper;

    public TaskLogService(TaskLogMapper taskLogMapper) {
        this.taskLogMapper = taskLogMapper;
    }

    public Page<TaskLogEntity> page(int pageNum, int pageSize, Long taskId) {
        LambdaQueryWrapper<TaskLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(TaskLogEntity::getTaskId, taskId);
        }
        wrapper.orderByAsc(TaskLogEntity::getId);
        return taskLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public void appendLog(LogEntryDTO dto) {
        TaskLogEntity entity = new TaskLogEntity();
        entity.setTaskId(dto.getTaskId());
        entity.setLogLevel(dto.getLevel());
        entity.setContent(dto.getContent());
        entity.setLogSource("agent");
        taskLogMapper.insert(entity);
    }

    @Transactional
    public void batchAppend(List<LogEntryDTO> logList) {
        for (LogEntryDTO dto : logList) {
            appendLog(dto);
        }
    }

    public void cleanExpiredLogs(int retentionDays) {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        taskLogMapper.delete(new LambdaQueryWrapper<TaskLogEntity>()
                .lt(TaskLogEntity::getCreateTime, before));
    }
}
