package com.hdfs.transfer.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("verify_result")
public class VerifyResultEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String execId;
    private String verifyStatus;
    private Long sourceFileCount;
    private Long targetFileCount;
    private Long sourceTotalSize;
    private Long targetTotalSize;
    private String diffFileList;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String taskName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getExecId() { return execId; }
    public void setExecId(String execId) { this.execId = execId; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public Long getSourceFileCount() { return sourceFileCount; }
    public void setSourceFileCount(Long sourceFileCount) { this.sourceFileCount = sourceFileCount; }
    public Long getTargetFileCount() { return targetFileCount; }
    public void setTargetFileCount(Long targetFileCount) { this.targetFileCount = targetFileCount; }
    public Long getSourceTotalSize() { return sourceTotalSize; }
    public void setSourceTotalSize(Long sourceTotalSize) { this.sourceTotalSize = sourceTotalSize; }
    public Long getTargetTotalSize() { return targetTotalSize; }
    public void setTargetTotalSize(Long targetTotalSize) { this.targetTotalSize = targetTotalSize; }
    public String getDiffFileList() { return diffFileList; }
    public void setDiffFileList(String diffFileList) { this.diffFileList = diffFileList; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
}
