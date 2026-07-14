package com.hdfs.transfer.common.enums;

public enum AgentStatusEnum {
    ONLINE("online", "在线"),
    OFFLINE("offline", "离线"),
    BUSY("busy", "忙碌");

    private final String code;
    private final String desc;

    AgentStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
