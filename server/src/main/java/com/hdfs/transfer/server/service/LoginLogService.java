package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.server.entity.LoginLogEntity;
import com.hdfs.transfer.server.mapper.LoginLogMapper;
import org.springframework.stereotype.Service;

@Service
public class LoginLogService {

    private final LoginLogMapper logMapper;

    public LoginLogService(LoginLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void record(String username, String loginIp) {
        LoginLogEntity log = new LoginLogEntity();
        log.setUsername(username);
        log.setLoginIp(loginIp);
        logMapper.insert(log);
    }

    public Page<LoginLogEntity> page(int pageNum, int pageSize, String username) {
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like(LoginLogEntity::getUsername, username.trim());
        }
        wrapper.orderByDesc(LoginLogEntity::getCreateTime);
        return logMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
