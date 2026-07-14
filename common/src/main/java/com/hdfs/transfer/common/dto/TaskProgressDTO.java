package com.hdfs.transfer.common.dto;

public class TaskProgressDTO {
    private Long taskId;
    private long totalSizeBytes;
    private long completedSizeBytes;
    private double progressPercent;
    private long totalFiles;
    private long completedFiles;
    private double transferRateMbps;
    private String currentFile;
    private String status;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
    public long getCompletedSizeBytes() { return completedSizeBytes; }
    public void setCompletedSizeBytes(long completedSizeBytes) { this.completedSizeBytes = completedSizeBytes; }
    public double getProgressPercent() { return progressPercent; }
    public void setProgressPercent(double progressPercent) { this.progressPercent = progressPercent; }
    public long getTotalFiles() { return totalFiles; }
    public void setTotalFiles(long totalFiles) { this.totalFiles = totalFiles; }
    public long getCompletedFiles() { return completedFiles; }
    public void setCompletedFiles(long completedFiles) { this.completedFiles = completedFiles; }
    public double getTransferRateMbps() { return transferRateMbps; }
    public void setTransferRateMbps(double transferRateMbps) { this.transferRateMbps = transferRateMbps; }
    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
