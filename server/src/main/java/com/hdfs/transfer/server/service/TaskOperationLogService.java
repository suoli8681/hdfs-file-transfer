package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.server.entity.TaskOperationLogEntity;
import com.hdfs.transfer.server.mapper.TaskOperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskOperationLogService {

    private final TaskOperationLogMapper logMapper;

    public TaskOperationLogService(TaskOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void record(Long taskId, String taskName, String operation, String operator, String detail) {
        TaskOperationLogEntity log = new TaskOperationLogEntity();
        log.setTaskId(taskId);
        log.setTaskName(taskName);
        log.setOperation(operation);
        log.setOperator(operator);
        log.setDetail(detail);
        logMapper.insert(log);
    }

    public List<TaskOperationLogEntity> listByTaskId(Long taskId) {
        return logMapper.selectList(
                new LambdaQueryWrapper<TaskOperationLogEntity>()
                        .eq(TaskOperationLogEntity::getTaskId, taskId)
                        .orderByDesc(TaskOperationLogEntity::getCreateTime));
    }

    public Page<TaskOperationLogEntity> page(int pageNum, int pageSize, Long taskId) {
        LambdaQueryWrapper<TaskOperationLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(TaskOperationLogEntity::getTaskId, taskId);
        }
        wrapper.orderByDesc(TaskOperationLogEntity::getCreateTime);
        return logMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}