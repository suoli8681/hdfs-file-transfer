package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.mapper.ClusterConfigMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ClusterConfigService {

    private final ClusterConfigMapper clusterConfigMapper;

    public ClusterConfigService(ClusterConfigMapper clusterConfigMapper) {
        this.clusterConfigMapper = clusterConfigMapper;
    }

    public Page<ClusterConfigEntity> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<ClusterConfigEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(ClusterConfigEntity::getClusterName, keyword)
                   .or().like(ClusterConfigEntity::getNameNodeRpc, keyword);
        }
        wrapper.orderByDesc(ClusterConfigEntity::getCreateTime);
        return clusterConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public ClusterConfigEntity getById(Long id) {
        return clusterConfigMapper.selectById(id);
    }

    public List<ClusterConfigEntity> listAll() {
        return clusterConfigMapper.selectList(new LambdaQueryWrapper<ClusterConfigEntity>()
                .eq(ClusterConfigEntity::getIsEnabled, 1));
    }

    public void add(ClusterConfigEntity entity) {
        clusterConfigMapper.insert(entity);
    }

    public void update(ClusterConfigEntity entity) {
        clusterConfigMapper.updateById(entity);
    }

    public void delete(Long id) {
        clusterConfigMapper.deleteById(id);
    }

    public String testConnect(Long id) {
        ClusterConfigEntity cluster = clusterConfigMapper.selectById(id);
        if (cluster == null) return "集群配置不存在";
        String rpc = cluster.getNameNodeRpc();
        if (rpc == null || rpc.isEmpty()) return "未配置NameNode RPC地址";
        try {
            String host = rpc.contains(":") ? rpc.split(":")[0].trim() : rpc.trim();
            int port = rpc.contains(":") ? Integer.parseInt(rpc.split(":")[1].trim()) : 8020;
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                return null;
            }
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }
}
