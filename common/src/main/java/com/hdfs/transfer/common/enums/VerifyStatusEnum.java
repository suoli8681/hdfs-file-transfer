package com.hdfs.transfer.common.enums;

public enum VerifyStatusEnum {
    PENDING("pending", "待校验"),
    MATCH("match", "一致"),
    MISMATCH("mismatch", "不一致"),
    ERROR("error", "校验异常");

    private final String code;
    private final String desc;

    VerifyStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
