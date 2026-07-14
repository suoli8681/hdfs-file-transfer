package com.hdfs.transfer.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("cluster_config")
public class ClusterConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clusterName;
    private String clusterType;
    private String nameService;
    private String nameNodeRpc;
    private String nameNodeHttp;
    private String hdfsUser;
    private String confDir;
    private String description;
    private Integer isEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getClusterType() { return clusterType; }
    public void setClusterType(String clusterType) { this.clusterType = clusterType; }
    public String getNameService() { return nameService; }
    public void setNameService(String nameService) { this.nameService = nameService; }
    public String getNameNodeRpc() { return nameNodeRpc; }
    public void setNameNodeRpc(String nameNodeRpc) { this.nameNodeRpc = nameNodeRpc; }
    public String getNameNodeHttp() { return nameNodeHttp; }
    public void setNameNodeHttp(String nameNodeHttp) { this.nameNodeHttp = nameNodeHttp; }
    public String getHdfsUser() { return hdfsUser; }
    public void setHdfsUser(String hdfsUser) { this.hdfsUser = hdfsUser; }
    public String getConfDir() { return confDir; }
    public void setConfDir(String confDir) { this.confDir = confDir; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Integer isEnabled) { this.isEnabled = isEnabled; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
