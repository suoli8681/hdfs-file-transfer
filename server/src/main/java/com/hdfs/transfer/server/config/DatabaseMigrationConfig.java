package com.hdfs.transfer.server.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DatabaseMigrationConfig {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationConfig(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS task_instance (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  parent_task_id BIGINT NOT NULL COMMENT '父任务ID（migration_task.id）'," +
            "  instance_name VARCHAR(256) NOT NULL COMMENT '实例名称（任务名称_yyyyMMddHHmmss）'," +
            "  source_cluster_id BIGINT DEFAULT NULL COMMENT '源集群ID'," +
            "  source_path VARCHAR(1024) NOT NULL COMMENT '源路径'," +
            "  target_cluster_id BIGINT DEFAULT NULL COMMENT '目标集群ID'," +
            "  target_path VARCHAR(1024) NOT NULL COMMENT '目标路径'," +
            "  distcp_options VARCHAR(2048) DEFAULT NULL COMMENT 'distcp参数'," +
            "  agent_id VARCHAR(64) DEFAULT NULL COMMENT '执行Agent标识'," +
            "  status VARCHAR(32) DEFAULT 'pending' COMMENT '实例状态'," +
            "  retry_count INT DEFAULT 0 COMMENT '已重试次数'," +
            "  max_retry_count INT DEFAULT 3 COMMENT '最大重试次数'," +
            "  priority INT DEFAULT 5 COMMENT '优先级'," +
            "  total_files BIGINT DEFAULT 0 COMMENT '总文件数'," +
            "  total_size BIGINT DEFAULT 0 COMMENT '总数据量'," +
            "  completed_files BIGINT DEFAULT 0 COMMENT '已完成文件数'," +
            "  completed_size BIGINT DEFAULT 0 COMMENT '已完成数据量'," +
            "  last_exec_time VARCHAR(32) DEFAULT NULL COMMENT '最后执行时间'," +
            "  complete_time VARCHAR(32) DEFAULT NULL COMMENT '完成时间'," +
            "  error_msg TEXT DEFAULT NULL COMMENT '错误信息'," +
            "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") COMMENT '任务实例表'");
    }
}
