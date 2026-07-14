package com.hdfs.transfer.common.enums;

public enum TaskStatusEnum {
    PENDING("pending", "待执行"),
    RUNNING("running", "运行中"),
    SUCCESS("success", "已完成"),
    FAILED("failed", "失败"),
    STOPPED("stopped", "已停止"),
    RETRYING("retrying", "重试中");

    private final String code;
    private final String desc;

    TaskStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static TaskStatusEnum fromCode(String code) {
        for (TaskStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return PENDING;
    }
}
