package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.common.dto.VerifyResultDTO;
import com.hdfs.transfer.server.entity.VerifyResultEntity;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.mapper.VerifyResultMapper;
import com.hdfs.transfer.server.mapper.MigrationTaskMapper;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VerifyResultService {

    private final VerifyResultMapper verifyResultMapper;
    private final MigrationTaskMapper taskMapper;

    public VerifyResultService(VerifyResultMapper verifyResultMapper, MigrationTaskMapper taskMapper) {
        this.verifyResultMapper = verifyResultMapper;
        this.taskMapper = taskMapper;
    }

    public Page<VerifyResultEntity> page(int pageNum, int pageSize, String taskName) {
        LambdaQueryWrapper<VerifyResultEntity> wrapper = new LambdaQueryWrapper<>();
        if (taskName != null && !taskName.isEmpty()) {
            wrapper.apply("EXISTS (SELECT 1 FROM migration_task mt WHERE mt.id = verify_result.task_id AND mt.task_name LIKE {0})", "%" + taskName + "%");
        }
        wrapper.orderByDesc(VerifyResultEntity::getCreateTime);
        Page<VerifyResultEntity> page = verifyResultMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        enrichTaskNames(page.getRecords());
        return page;
    }

    private void enrichTaskNames(java.util.List<VerifyResultEntity> results) {
        for (VerifyResultEntity r : results) {
            if (r.getTaskId() != null) {
                MigrationTaskEntity task = taskMapper.selectById(r.getTaskId());
                if (task != null) r.setTaskName(task.getTaskName());
            }
        }
    }

    public VerifyResultEntity getLatestByTaskId(Long taskId) {
        return verifyResultMapper.selectOne(
                new LambdaQueryWrapper<VerifyResultEntity>()
                        .eq(VerifyResultEntity::getTaskId, taskId)
                        .orderByDesc(VerifyResultEntity::getCreateTime)
                        .last("LIMIT 1"));
    }

    @Transactional
    public void saveResult(VerifyResultDTO dto) {
        VerifyResultEntity entity = new VerifyResultEntity();
        entity.setTaskId(dto.getTaskId());
        entity.setVerifyStatus(dto.getVerifyStatus());
        entity.setSourceFileCount(dto.getSourceFileCount());
        entity.setTargetFileCount(dto.getTargetFileCount());
        entity.setSourceTotalSize(dto.getSourceTotalSize());
        entity.setTargetTotalSize(dto.getTargetTotalSize());
        if (dto.getDiffDetails() != null) {
            entity.setDiffFileList(JSON.toJSONString(dto.getDiffDetails()));
        }
        entity.setErrorMessage(dto.getErrorMessage());
        verifyResultMapper.insert(entity);
    }
}
