package com.hdfs.transfer.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("migration_task")
public class MigrationTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String taskType;
    private Long sourceClusterId;
    private String sourcePath;
    private Long targetClusterId;
    private String targetPath;
    private String distcpOptions;
    private String cronExpr;
    private String agentId;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private Integer priority;
    private Long totalFiles;
    private Long totalSize;
    private Long completedFiles;
    private Long completedSize;
    private String lastExecTime;
    private String nextExecTime;
    private String completeTime;
    private String errorMsg;

    @TableField(exist = false)
    private String sourceClusterName;
    @TableField(exist = false)
    private String targetClusterName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getSourceClusterId() { return sourceClusterId; }
    public void setSourceClusterId(Long sourceClusterId) { this.sourceClusterId = sourceClusterId; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public Long getTargetClusterId() { return targetClusterId; }
    public void setTargetClusterId(Long targetClusterId) { this.targetClusterId = targetClusterId; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getDistcpOptions() { return distcpOptions; }
    public void setDistcpOptions(String distcpOptions) { this.distcpOptions = distcpOptions; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Long getTotalFiles() { return totalFiles; }
    public void setTotalFiles(Long totalFiles) { this.totalFiles = totalFiles; }
    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
    public Long getCompletedFiles() { return completedFiles; }
    public void setCompletedFiles(Long completedFiles) { this.completedFiles = completedFiles; }
    public Long getCompletedSize() { return completedSize; }
    public void setCompletedSize(Long completedSize) { this.completedSize = completedSize; }
    public String getLastExecTime() { return lastExecTime; }
    public void setLastExecTime(String lastExecTime) { this.lastExecTime = lastExecTime; }
    public String getNextExecTime() { return nextExecTime; }
    public void setNextExecTime(String nextExecTime) { this.nextExecTime = nextExecTime; }
    public String getCompleteTime() { return completeTime; }
    public void setCompleteTime(String completeTime) { this.completeTime = completeTime; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getSourceClusterName() { return sourceClusterName; }
    public void setSourceClusterName(String sourceClusterName) { this.sourceClusterName = sourceClusterName; }
    public String getTargetClusterName() { return targetClusterName; }
    public void setTargetClusterName(String targetClusterName) { this.targetClusterName = targetClusterName; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
