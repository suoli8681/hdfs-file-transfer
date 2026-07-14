package com.hdfs.transfer.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hdfs.transfer.server.entity.TaskLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLogEntity> {
}
