package com.hdfs.transfer.common.enums;

public enum TaskTypeEnum {
    ONCE("once", "一次性"),
    SCHEDULED("scheduled", "定时同步");

    private final String code;
    private final String desc;

    TaskTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
