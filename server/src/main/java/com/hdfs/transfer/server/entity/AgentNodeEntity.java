package com.hdfs.transfer.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("agent_node")
public class AgentNodeEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentId;
    private String agentHost;
    private Integer agentPort;
    private String status;
    private Integer runningTaskCount;
    private Integer maxParallelTasks;
    private double cpuUsage;
    private double memoryUsage;
    private String version;
    private String remark;
    private LocalDateTime lastHeartbeatTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentHost() { return agentHost; }
    public void setAgentHost(String agentHost) { this.agentHost = agentHost; }
    public Integer getAgentPort() { return agentPort; }
    public void setAgentPort(Integer agentPort) { this.agentPort = agentPort; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRunningTaskCount() { return runningTaskCount; }
    public void setRunningTaskCount(Integer runningTaskCount) { this.runningTaskCount = runningTaskCount; }
    public Integer getMaxParallelTasks() { return maxParallelTasks; }
    public void setMaxParallelTasks(Integer maxParallelTasks) { this.maxParallelTasks = maxParallelTasks; }
    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getLastHeartbeatTime() { return lastHeartbeatTime; }
    public void setLastHeartbeatTime(LocalDateTime lastHeartbeatTime) { this.lastHeartbeatTime = lastHeartbeatTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
