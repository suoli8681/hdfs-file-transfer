package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.mapper.TaskInstanceMapper;
import com.hdfs.transfer.server.mapper.ClusterConfigMapper;
import com.hdfs.transfer.server.mapper.MigrationTaskMapper;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
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
public class TaskInstanceService {

    private static final Logger log = LoggerFactory.getLogger(TaskInstanceService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TaskInstanceMapper instanceMapper;
    private final MigrationTaskMapper taskMapper;
    private final ClusterConfigMapper clusterConfigMapper;
    private final AgentNodeMapper agentNodeMapper;
    private final TaskOperationLogService operationLogService;
    private final RestTemplate agentRestTemplate;

    public TaskInstanceService(TaskInstanceMapper instanceMapper,
                                MigrationTaskMapper taskMapper,
                                ClusterConfigMapper clusterConfigMapper,
                                AgentNodeMapper agentNodeMapper,
                                TaskOperationLogService operationLogService) {
        this.instanceMapper = instanceMapper;
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

    public Page<TaskInstanceEntity> page(int pageNum, int pageSize, Long parentTaskId,
                                          String keyword, String status, String agentId,
                                          String startTime, String endTime) {
        LambdaQueryWrapper<TaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        if (parentTaskId != null) {
            wrapper.eq(TaskInstanceEntity::getParentTaskId, parentTaskId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(TaskInstanceEntity::getInstanceName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(TaskInstanceEntity::getStatus, status);
        }
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(TaskInstanceEntity::getAgentId, agentId);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(TaskInstanceEntity::getLastExecTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(TaskInstanceEntity::getCompleteTime, endTime);
        }
        wrapper.orderByDesc(TaskInstanceEntity::getCreateTime);
        Page<TaskInstanceEntity> page = instanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        enrichClusterNames(page.getRecords());
        return page;
    }

    public List<TaskInstanceEntity> listForExport(Long parentTaskId, String keyword, String status,
                                                   String agentId, String startTime, String endTime) {
        LambdaQueryWrapper<TaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        if (parentTaskId != null) {
            wrapper.eq(TaskInstanceEntity::getParentTaskId, parentTaskId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(TaskInstanceEntity::getInstanceName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(TaskInstanceEntity::getStatus, status);
        }
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(TaskInstanceEntity::getAgentId, agentId);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(TaskInstanceEntity::getLastExecTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(TaskInstanceEntity::getCompleteTime, endTime);
        }
        wrapper.orderByDesc(TaskInstanceEntity::getCreateTime);
        List<TaskInstanceEntity> list = instanceMapper.selectList(wrapper);
        enrichClusterNames(list);
        return list;
    }

    public TaskInstanceEntity getById(Long id) {
        TaskInstanceEntity entity = instanceMapper.selectById(id);
        if (entity != null) {
            enrichClusterNames(java.util.Collections.singletonList(entity));
        }
        return entity;
    }

    @Transactional
    public void createInstanceFromTemplate(Long templateId) {
        MigrationTaskEntity template = taskMapper.selectById(templateId);
        if (template == null) {
            log.warn("Template {} not found", templateId);
            return;
        }
        TaskInstanceEntity instance = new TaskInstanceEntity();
        instance.setParentTaskId(templateId);
        instance.setInstanceName(template.getTaskName() + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        instance.setSourceClusterId(template.getSourceClusterId());
        instance.setSourcePath(resolvePathExpression(template.getSourcePath()));
        instance.setTargetClusterId(template.getTargetClusterId());
        instance.setTargetPath(resolvePathExpression(template.getTargetPath()));
        instance.setDistcpOptions(template.getDistcpOptions());
        instance.setAgentId(template.getAgentId());
        instance.setStatus("pending");
        instance.setRetryCount(0);
        instance.setMaxRetryCount(template.getMaxRetryCount() != null ? template.getMaxRetryCount() : 3);
        instance.setPriority(template.getPriority() != null ? template.getPriority() : 5);
        instance.setTotalFiles(0L);
        instance.setTotalSize(0L);
        instance.setCompletedFiles(0L);
        instance.setCompletedSize(0L);
        instance.setLastExecTime(LocalDateTime.now().format(DTF));
        instanceMapper.insert(instance);
        operationLogService.record(templateId, template.getTaskName(), "execute",
                getCurrentUser(), "生成实例: " + instance.getInstanceName());
        log.info("Created instance {} from template {}", instance.getId(), templateId);
    }

    @Transactional
    public void stopByParentTaskId(Long parentTaskId) {
        List<TaskInstanceEntity> runningInstances = instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .eq(TaskInstanceEntity::getParentTaskId, parentTaskId)
                        .in(TaskInstanceEntity::getStatus, "pending", "dispatching", "running", "retrying"));
        for (TaskInstanceEntity inst : runningInstances) {
            inst.setStatus("stopped");
            inst.setCompleteTime(LocalDateTime.now().format(DTF));
            instanceMapper.updateById(inst);
        }
    }

    @Transactional
    public void killByParentTaskId(Long parentTaskId) {
        List<TaskInstanceEntity> runningInstances = instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .eq(TaskInstanceEntity::getParentTaskId, parentTaskId)
                        .in(TaskInstanceEntity::getStatus, "running", "retrying", "dispatching"));
        for (TaskInstanceEntity inst : runningInstances) {
            notifyAgent(inst.getId(), inst.getAgentId(), "kill");
            inst.setStatus("killed");
            inst.setCompleteTime(LocalDateTime.now().format(DTF));
            instanceMapper.updateById(inst);
        }
    }

    @Transactional
    public boolean forceKill(Long id) {
        TaskInstanceEntity entity = instanceMapper.selectById(id);
        if (entity == null) return false;
        notifyAgent(id, entity.getAgentId(), "kill");
        entity.setStatus("killed");
        entity.setCompleteTime(LocalDateTime.now().format(DTF));
        instanceMapper.updateById(entity);
        return true;
    }

    public long countByParentTaskId(Long parentTaskId) {
        return instanceMapper.selectCount(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .eq(TaskInstanceEntity::getParentTaskId, parentTaskId));
    }

    @Transactional
    public void deleteByParentTaskId(Long parentTaskId) {
        List<TaskInstanceEntity> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .eq(TaskInstanceEntity::getParentTaskId, parentTaskId));
        for (TaskInstanceEntity inst : instances) {
            instanceMapper.deleteById(inst.getId());
        }
    }

    @Transactional
    public void updateProgress(Long instanceId, long completedFiles, long completedSize,
                               long totalFiles, long totalSize, String status, String errorMsg) {
        TaskInstanceEntity entity = instanceMapper.selectById(instanceId);
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
        instanceMapper.updateById(entity);
    }

    public List<TaskInstanceEntity> listDispatched(String agentId) {
        return instanceMapper.selectList(
                new LambdaQueryWrapper<TaskInstanceEntity>()
                        .eq(TaskInstanceEntity::getAgentId, agentId)
                        .eq(TaskInstanceEntity::getStatus, "dispatching"));
    }

    @Transactional
    public boolean updateStatusIfMatch(Long instanceId, String newStatus, String expectedStatus) {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TaskInstanceEntity> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", instanceId).eq("status", expectedStatus).set("status", newStatus);
        return instanceMapper.update(null, wrapper) > 0;
    }

    private void enrichClusterNames(List<TaskInstanceEntity> instances) {
        for (TaskInstanceEntity inst : instances) {
            if (inst.getSourceClusterId() != null) {
                ClusterConfigEntity src = clusterConfigMapper.selectById(inst.getSourceClusterId());
                if (src != null) inst.setSourceClusterName(src.getClusterName());
            }
            if (inst.getTargetClusterId() != null) {
                ClusterConfigEntity tgt = clusterConfigMapper.selectById(inst.getTargetClusterId());
                if (tgt != null) inst.setTargetClusterName(tgt.getClusterName());
            }
        }
    }

    private static final java.util.regex.Pattern EXPR_PATTERN =
            java.util.regex.Pattern.compile("\\$\\{([Yy]{4}[-]?[Mm]{2}[-]?[Dd]{2}(?:[ T]?[Hh]{2}(?::?[Mm]{2}(?::?[Ss]{2})?)?)?)([+-]\\d+)?\\}");

    private String resolvePathExpression(String path) {
        if (path == null || path.isEmpty() || !path.contains("${")) return path;
        java.util.regex.Matcher matcher = EXPR_PATTERN.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String formatPart = matcher.group(1);
            String offsetPart = matcher.group(2);
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.now();
            if (offsetPart != null) {
                int offset = Integer.parseInt(offsetPart);
                if (formatPart.contains("ss") || formatPart.contains("SS")) {
                    dateTime = dateTime.plusSeconds(offset);
                } else if ((formatPart.contains("mm") && (formatPart.contains("HH") || formatPart.contains("hh")))
                        || (formatPart.contains("MM") && formatPart.contains(":"))) {
                    dateTime = dateTime.plusMinutes(offset);
                } else if (formatPart.contains("HH") || formatPart.contains("hh")) {
                    dateTime = dateTime.plusHours(offset);
                } else if (formatPart.contains("dd") || formatPart.contains("DD")) {
                    dateTime = dateTime.plusDays(offset);
                } else if (formatPart.contains("MM")) {
                    dateTime = dateTime.plusMonths(offset);
                } else {
                    dateTime = dateTime.plusYears(offset);
                }
            }
            String javaFormat = formatPart
                    .replace("YYYY", "yyyy")
                    .replace("DD", "dd")
                    .replace("HH", "HH");
            String resolved = dateTime.format(java.time.format.DateTimeFormatter.ofPattern(javaFormat));
            matcher.appendReplacement(sb, resolved);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void notifyAgent(Long instanceId, String agentId, String action) {
        if (agentId == null || agentId.isEmpty()) {
            log.warn("Cannot notify agent for instance {}: no agentId", instanceId);
            return;
        }
        AgentNodeEntity agent = agentNodeMapper.selectOne(
                new LambdaQueryWrapper<AgentNodeEntity>().eq(AgentNodeEntity::getAgentId, agentId));
        if (agent == null) {
            log.warn("Cannot notify agent for instance {}: agent {} not found", instanceId, agentId);
            return;
        }
        String host = agent.getAgentHost();
        Integer port = agent.getAgentPort() != null ? agent.getAgentPort() : 8081;
        String url = "http://" + host + ":" + port + "/api/agent/task/" + instanceId + "/" + action;
        try {
            agentRestTemplate.postForEntity(url, null, Object.class);
            log.info("Notified agent {} to {} instance {}", agentId, action, instanceId);
        } catch (Exception e) {
            log.warn("Failed to notify agent {} to {} instance {}: {}", agentId, action, instanceId, e.getMessage());
        }
    }
}
