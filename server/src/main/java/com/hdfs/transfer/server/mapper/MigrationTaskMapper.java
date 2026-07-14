package com.hdfs.transfer.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MigrationTaskMapper extends BaseMapper<MigrationTaskEntity> {
}
