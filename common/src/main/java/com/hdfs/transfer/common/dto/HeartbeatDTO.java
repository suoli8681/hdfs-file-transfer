package com.hdfs.transfer.common.dto;

import java.util.List;

public class HeartbeatDTO {
    private String agentId;
    private String agentHost;
    private String status;
    private int runningTaskCount;
    private int maxParallelTasks;
    private double cpuUsage;
    private double memoryUsage;
    private List<TaskProgressDTO> taskProgressList;
    private long timestamp;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentHost() { return agentHost; }
    public void setAgentHost(String agentHost) { this.agentHost = agentHost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRunningTaskCount() { return runningTaskCount; }
    public void setRunningTaskCount(int runningTaskCount) { this.runningTaskCount = runningTaskCount; }
    public int getMaxParallelTasks() { return maxParallelTasks; }
    public void setMaxParallelTasks(int maxParallelTasks) { this.maxParallelTasks = maxParallelTasks; }
    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
    public List<TaskProgressDTO> getTaskProgressList() { return taskProgressList; }
    public void setTaskProgressList(List<TaskProgressDTO> taskProgressList) { this.taskProgressList = taskProgressList; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
