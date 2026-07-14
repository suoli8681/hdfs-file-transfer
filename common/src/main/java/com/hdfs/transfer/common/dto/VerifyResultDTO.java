package com.hdfs.transfer.common.dto;

import java.util.List;

public class VerifyResultDTO {
    private Long taskId;
    private String verifyStatus;
    private long sourceFileCount;
    private long targetFileCount;
    private long sourceTotalSize;
    private long targetTotalSize;
    private List<String> diffFiles;
    private List<DiffFileInfo> diffDetails;
    private String errorMessage;
    private long timestamp;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public long getSourceFileCount() { return sourceFileCount; }
    public void setSourceFileCount(long sourceFileCount) { this.sourceFileCount = sourceFileCount; }
    public long getTargetFileCount() { return targetFileCount; }
    public void setTargetFileCount(long targetFileCount) { this.targetFileCount = targetFileCount; }
    public long getSourceTotalSize() { return sourceTotalSize; }
    public void setSourceTotalSize(long sourceTotalSize) { this.sourceTotalSize = sourceTotalSize; }
    public long getTargetTotalSize() { return targetTotalSize; }
    public void setTargetTotalSize(long targetTotalSize) { this.targetTotalSize = targetTotalSize; }
    public List<String> getDiffFiles() { return diffFiles; }
    public void setDiffFiles(List<String> diffFiles) { this.diffFiles = diffFiles; }
    public List<DiffFileInfo> getDiffDetails() { return diffDetails; }
    public void setDiffDetails(List<DiffFileInfo> diffDetails) { this.diffDetails = diffDetails; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public static class DiffFileInfo {
        private String filePath;
        private long sourceSize;
        private long targetSize;
        private String diffType;

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public long getSourceSize() { return sourceSize; }
        public void setSourceSize(long sourceSize) { this.sourceSize = sourceSize; }
        public long getTargetSize() { return targetSize; }
        public void setTargetSize(long targetSize) { this.targetSize = targetSize; }
        public String getDiffType() { return diffType; }
        public void setDiffType(String diffType) { this.diffType = diffType; }
    }
}
