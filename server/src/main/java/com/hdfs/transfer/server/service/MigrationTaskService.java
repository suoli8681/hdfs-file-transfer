package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.mapper.MigrationTaskMapper;
import com.hdfs.transfer.server.mapper.ClusterConfigMapper;
import com.hdfs.transfer.server.mapper.AgentNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MigrationTaskService {

    private static final Logger log = LoggerFactory.getLogger(MigrationTaskService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MigrationTaskMapper taskMapper;
    private final ClusterConfigMapper clusterConfigMapper;
    private final AgentNodeMapper agentNodeMapper;
    private final TaskOperationLogService operationLogService;
    private final RestTemplate agentRestTemplate;

    public MigrationTaskService(MigrationTaskMapper taskMapper,
                                ClusterConfigMapper clusterConfigMapper,
                                AgentNodeMapper agentNodeMapper,
                                TaskOperationLogService operationLogService) {
        this.taskMapper = taskMapper;
        this.clusterConfigMapper = clusterConfigMapper;
        this.agentNodeMapper = agentNodeMapper;
        this.operationLogService = operationLogService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.agentRestTemplate = new RestTemplate(factory);
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
        return taskMapper.selectById(id);
    }

    public List<MigrationTaskEntity> listPendingTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<MigrationTaskEntity>()
                .in(MigrationTaskEntity::getStatus, "pending", "retrying"));
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
    public boolean start(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        // 禁止重复启动：running/dispatching/retryging 状态不可启动
        String currentStatus = entity.getStatus();
        if ("running".equals(currentStatus) || "dispatching".equals(currentStatus) || "retrying".equals(currentStatus)) {
            throw new RuntimeException("任务正在运行中，不可重复启动");
        }
        entity.setStatus("pending");
        entity.setRetryCount(0);
        entity.setLastExecTime(LocalDateTime.now().format(DTF));
        entity.setCompleteTime(null);
        entity.setErrorMsg(null);
        taskMapper.updateById(entity);
        operationLogService.record(id, entity.getTaskName(), "start",
                getCurrentUser(), "启动任务");
        return true;
    }

    @Transactional
    public boolean stop(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        String currentStatus = entity.getStatus();
        if (!"pending".equals(currentStatus) && !"dispatching".equals(currentStatus)) {
            throw new RuntimeException("仅待执行或派发中的任务可以停止");
        }
        entity.setStatus("stopped");
        entity.setCompleteTime(LocalDateTime.now().format(DTF));
        taskMapper.updateById(entity);
        operationLogService.record(id, entity.getTaskName(), "stop",
                getCurrentUser(), "停止任务（取消分发）");
        return true;
    }

    @Transactional
    public boolean forceKill(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return false;
        notifyAgent(id, entity.getAgentId(), "kill");
        entity.setStatus("killed");
        entity.setCompleteTime(LocalDateTime.now().format(DTF));
        taskMapper.updateById(entity);
        operationLogService.record(id, entity.getTaskName(), "kill",
                getCurrentUser(), "强制终止任务（含目标端文件清理）");
        return true;
    }

    private void notifyAgent(Long taskId, String agentId, String action) {
        if (agentId == null || agentId.isEmpty()) {
            log.warn("Cannot notify agent for task {}: no agentId", taskId);
            return;
        }
        AgentNodeEntity agent = agentNodeMapper.selectOne(
                new LambdaQueryWrapper<AgentNodeEntity>().eq(AgentNodeEntity::getAgentId, agentId));
        if (agent == null) {
            log.warn("Cannot notify agent for task {}: agent {} not found", taskId, agentId);
            return;
        }
        String host = agent.getAgentHost();
        Integer port = agent.getAgentPort() != null ? agent.getAgentPort() : 8081;
        String url = "http://" + host + ":" + port + "/api/agent/task/" + taskId + "/" + action;
        try {
            agentRestTemplate.postForEntity(url, null, Object.class);
            log.info("Notified agent {} to {} task {}", agentId, action, taskId);
        } catch (Exception e) {
            log.warn("Failed to notify agent {} to {} task {}: {}", agentId, action, taskId, e.getMessage());
        }
    }

    @Transactional
    public List<MigrationTaskEntity> listDispatched(String agentId) {
        return taskMapper.selectList(new LambdaQueryWrapper<MigrationTaskEntity>()
                .eq(MigrationTaskEntity::getAgentId, agentId)
                .eq(MigrationTaskEntity::getStatus, "dispatching"));
    }

    @Transactional
    public void updateEntity(MigrationTaskEntity entity) {
        taskMapper.updateById(entity);
    }

    @Transactional
    public boolean updateStatusIfMatch(Long taskId, String newStatus, String expectedStatus) {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<MigrationTaskEntity> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", taskId).eq("status", expectedStatus).set("status", newStatus);
        return taskMapper.update(null, wrapper) > 0;
    }

    public void delete(Long id) {
        MigrationTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) return;
        if ("killed".equals(entity.getStatus())) {
            throw new RuntimeException("已终止的任务不可删除");
        }
        String taskName = entity.getTaskName();
        taskMapper.deleteById(id);
        operationLogService.record(id, taskName, "delete",
                getCurrentUser(), "删除任务: " + taskName);
    }

    @Transactional
    public void updateProgress(Long taskId, long completedFiles, long completedSize,
                               long totalFiles, long totalSize, String status, String errorMsg) {
        MigrationTaskEntity entity = taskMapper.selectById(taskId);
        if (entity == null) return;
        entity.setCompletedFiles(completedFiles);
        entity.setCompletedSize(completedSize);
        if (totalFiles > 0) {
            entity.setTotalFiles(totalFiles);
        }
        if (totalSize > 0) {
            entity.setTotalSize(totalSize);
        }
        entity.setStatus(status);
        if (errorMsg != null) {
            entity.setErrorMsg(errorMsg);
        }
        if ("success".equals(status) || "failed".equals(status)) {
            entity.setCompleteTime(LocalDateTime.now().format(DTF));
        }
        taskMapper.updateById(entity);
    }

    private Long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return null;
        }
    }
}