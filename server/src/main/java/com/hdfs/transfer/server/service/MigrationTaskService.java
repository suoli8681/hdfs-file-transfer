package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.mapper.MigrationTaskMapper;
import com.hdfs.transfer.server.mapper.ClusterConfigMapper;
import com.hdfs.transfer.server.scheduler.CronTaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MigrationTaskService {

    private static final Logger log = LoggerFactory.getLogger(MigrationTaskService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MigrationTaskMapper taskMapper;
    private final ClusterConfigMapper clusterConfigMapper;
    private final TaskOperationLogService operationLogService;
    private final CronTaskManager cronTaskManager;
    private final TaskInstanceService instanceService;

    public MigrationTaskService(MigrationTaskMapper taskMapper,
                                 ClusterConfigMapper clusterConfigMapper,
                                 TaskOperationLogService operationLogService,
                                 CronTaskManager cronTaskManager,
                                 TaskInstanceService instanceService) {
        this.taskMapper = taskMapper;
        this.clusterConfigMapper = clusterConfigMapper;
        this.operationLogService = operationLogService;
        this.cronTaskManager = cronTaskManager;
        this.instanceService = instanceService;
    }

    private String getCurrentUser() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    public Page<MigrationTaskEntity> page(int pageNum, int pageSize, String keyword, String status,
                                           String agentId, String startTime, String endTime) {
        LambdaQueryWrapper<MigrationTaskEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MigrationTaskEntity::getTaskName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MigrationTaskEntity::getStatus, status);
        }
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(MigrationTaskEntity::getAgentId, agentId);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(MigrationTaskEntity::getLastExecTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(MigrationTaskEntity::getCompleteTime, endTime);
        }
        wrapper.orderByDesc(MigrationTaskEntity::getCreateTime);
        Page<MigrationTaskEntity> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        enrichClusterNames(page.getRecords());
        return page;
    }

    public List<MigrationTaskEntity> listForExport(String keyword, String status,
                                                   String agentId, String startTime, String endTime) {
        LambdaQueryWrapper<MigrationTaskEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MigrationTaskEntity::getTaskName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MigrationTaskEntity::getStatus, status);
        }
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(MigrationTaskEntity::getAgentId, agentId);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(MigrationTaskEntity::getLastExecTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(MigrationTaskEntity::getCompleteTime, endTime);
        }
        wrapper.orderByDesc(MigrationTaskEntity::getCreateTime);
        List<MigrationTaskEntity> list = taskMapper.selectList(wrapper);
        enrichClusterNames(list);
        return list;
    }

    private void enrichClusterNames(List<MigrationTaskEntity> tasks) {
        for (MigrationTaskEntity task : tasks) {
            if (task.getSourceClusterId() != null) {
                ClusterConfigEntity src = clusterConfigMapper.selectById(task.getSourceClusterId());
                if (src != null) task.setSourceClusterName(src.getClusterName());
            }
            if (task.getTargetClusterId() != null) {
                ClusterConfigEntity tgt = clusterConfigMapper.selectById(task.getTargetClusterId());
                if (tgt != null) task.setTargetClusterName(tgt.getClusterName());
            }
        }
    }

    public MigrationTaskEntity getById(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity != null) {
            enrichClusterNames(java.util.Collections.singletonList(entity));
        }
        return entity;
    }

    @Transactional
    public void add(TaskDTO dto) {
        if (taskMapper.selectCount(new LambdaQueryWrapper<MigrationTaskEntity>()
                .eq(MigrationTaskEntity::getTaskName, dto.getTaskName())) > 0) {
            throw new RuntimeException("任务名称已存在: " + dto.getTaskName());
        }
        MigrationTaskEntity entity = new MigrationTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setTaskType(dto.getTaskType());
        entity.setSourceClusterId(parseLong(dto.getSourceCluster()));
        entity.setSourcePath(dto.getSourcePath());
        entity.setTargetClusterId(parseLong(dto.getTargetCluster()));
        entity.setTargetPath(dto.getTargetPath());
        entity.setDistcpOptions(dto.getDistcpOptions());
        entity.setCronExpr(dto.getCronExpr());
        entity.setAgentId(dto.getAgentId());
        entity.setStatus("draft");
        entity.setRetryCount(0);
        entity.setMaxRetryCount(3);
        entity.setPriority(5);
        entity.setTotalFiles(0L);
        entity.setTotalSize(0L);
        entity.setCompletedFiles(0L);
        entity.setCompletedSize(0L);
        taskMapper.insert(entity);
        operationLogService.record(entity.getId(), entity.getTaskName(), "create",
                getCurrentUser(), "创建任务: " + entity.getTaskName());
    }

    @Transactional
    public void update(TaskDTO dto) {
        MigrationTaskEntity entity = taskMapper.selectById(dto.getTaskId());
        if (entity == null) return;
        if (dto.getTaskName() != null) entity.setTaskName(dto.getTaskName());
        if (dto.getSourcePath() != null) entity.setSourcePath(dto.getSourcePath());
        if (dto.getTargetPath() != null) entity.setTargetPath(dto.getTargetPath());
        if (dto.getDistcpOptions() != null) entity.setDistcpOptions(dto.getDistcpOptions());
        if (dto.getCronExpr() != null) entity.setCronExpr(dto.getCronExpr());
        if (dto.getAgentId() != null) entity.setAgentId(dto.getAgentId());
        taskMapper.updateById(entity);
        operationLogService.record(entity.getId(), entity.getTaskName(), "edit",
                getCurrentUser(), "编辑任务: " + entity.getTaskName());
    }

    @Transactional
    public boolean online(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        if (!"draft".equals(entity.getStatus()) && !"offline".equals(entity.getStatus())) {
            throw new RuntimeException("仅草稿或下线状态的任务可上线");
        }
        entity.setStatus("online");
        entity.setLastExecTime(LocalDateTime.now().format(DTF));
        taskMapper.updateById(entity);
        if ("scheduled".equals(entity.getTaskType())) {
            if (entity.getCronExpr() == null || entity.getCronExpr().isEmpty()) {
                throw new RuntimeException("定时任务缺少Cron表达式");
            }
            cronTaskManager.register(id, entity.getCronExpr());
        }
        operationLogService.record(id, entity.getTaskName(), "online",
                getCurrentUser(), "任务上线");
        return true;
    }

    @Transactional
    public boolean offline(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        if (!"online".equals(entity.getStatus())) {
            throw new RuntimeException("仅上线状态的任务可下线");
        }
        if ("scheduled".equals(entity.getTaskType())) {
            cronTaskManager.unregister(id);
        }
        entity.setStatus("offline");
        taskMapper.updateById(entity);
        operationLogService.record(id, entity.getTaskName(), "offline",
                getCurrentUser(), "任务下线");
        return true;
    }

    @Transactional
    public boolean execute(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        if (!"online".equals(entity.getStatus())) {
            throw new RuntimeException("仅上线状态的任务可执行");
        }
        instanceService.createInstanceFromTemplate(id);
        entity.setLastExecTime(LocalDateTime.now().format(DTF));
        taskMapper.updateById(entity);
        return true;
    }

    @Transactional
    public boolean forceKill(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        instanceService.killByParentTaskId(id);
        operationLogService.record(id, entity.getTaskName(), "kill",
                getCurrentUser(), "强制终止所有运行中实例");
        return true;
    }

    public void delete(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return;
        if ("online".equals(entity.getStatus())) {
            throw new RuntimeException("上线状态的任务不可删除，请先下线");
        }
        long instanceCount = instanceService.countByParentTaskId(id);
        if (instanceCount > 0) {
            throw new RuntimeException("存在" + instanceCount + "个任务实例，不可删除");
        }
        if ("scheduled".equals(entity.getTaskType())) {
            cronTaskManager.unregister(id);
        }
        String taskName = entity.getTaskName();
        taskMapper.deleteById(id);
        operationLogService.record(id, taskName, "delete",
                getCurrentUser(), "删除任务: " + taskName);
    }

    @Transactional
    public void updateProgress(Long instanceId, long completedFiles, long completedSize,
                               long totalFiles, long totalSize, String status, String errorMsg) {
        instanceService.updateProgress(instanceId, completedFiles, completedSize, totalFiles, totalSize, status, errorMsg);
    }

    public List<MigrationTaskEntity> listScheduledOnline() {
        return taskMapper.selectList(
                new LambdaQueryWrapper<MigrationTaskEntity>()
                        .eq(MigrationTaskEntity::getTaskType, "scheduled")
                        .eq(MigrationTaskEntity::getStatus, "online"));
    }

    private Long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return null;
        }
    }
}
