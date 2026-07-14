package com.hdfs.transfer.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("task_log")
public class TaskLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String logLevel;
    private String content;
    private Integer lineNumber;
    private String logSource;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getLogSource() { return logSource; }
    public void setLogSource(String logSource) { this.logSource = logSource; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
