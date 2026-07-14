package com.hdfs.transfer.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hdfs.transfer.server.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
    SysUserEntity selectByUsername(String username);
}