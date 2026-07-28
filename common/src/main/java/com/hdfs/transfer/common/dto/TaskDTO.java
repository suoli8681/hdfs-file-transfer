package com.hdfs.transfer.common.dto;

public class TaskDTO {
    private Long taskId;
    private String taskName;
    private String taskType;
    private String sourceCluster;
    private String sourcePath;
    private String targetCluster;
    private String targetPath;
    private String distcpOptions;
    private String cronExpr;
    private String agentId;
    private Boolean alertEnabled;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getSourceCluster() { return sourceCluster; }
    public void setSourceCluster(String sourceCluster) { this.sourceCluster = sourceCluster; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getTargetCluster() { return targetCluster; }
    public void setTargetCluster(String targetCluster) { this.targetCluster = targetCluster; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getDistcpOptions() { return distcpOptions; }
    public void setDistcpOptions(String distcpOptions) { this.distcpOptions = distcpOptions; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public Boolean getAlertEnabled() { return alertEnabled; }
    public void setAlertEnabled(Boolean alertEnabled) { this.alertEnabled = alertEnabled; }
}
