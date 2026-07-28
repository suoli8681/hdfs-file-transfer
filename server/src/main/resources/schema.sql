-- HDFS 文件迁移平台 数据库初始化脚本
-- 使用方法: mysql -h <host> -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS hdfs_transfer DEFAULT CHARSET utf8mb4;

USE hdfs_transfer;

-- 集群配置表
CREATE TABLE IF NOT EXISTS cluster_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_name VARCHAR(128) NOT NULL COMMENT '集群名称',
    cluster_type VARCHAR(32) NOT NULL DEFAULT 'hadoop' COMMENT '集群类型',
    name_service VARCHAR(256) DEFAULT NULL COMMENT 'nameservice名称',
    name_node_rpc VARCHAR(512) DEFAULT NULL COMMENT 'NameNode RPC地址',
    name_node_http VARCHAR(512) DEFAULT NULL COMMENT 'NameNode HTTP地址',
    hdfs_user VARCHAR(64) DEFAULT 'hdfs' COMMENT 'HDFS用户',
    conf_dir VARCHAR(512) DEFAULT NULL COMMENT 'Hadoop配置目录',
    description VARCHAR(512) DEFAULT NULL COMMENT '描述',
    is_enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '集群配置表';

-- Agent节点表
CREATE TABLE IF NOT EXISTS agent_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Agent标识',
    agent_host VARCHAR(128) NOT NULL COMMENT 'Agent主机地址',
    agent_port INT DEFAULT 8081 COMMENT 'Agent端口',
    status VARCHAR(32) DEFAULT 'offline' COMMENT '状态: online/busy/offline',
    running_task_count INT DEFAULT 0 COMMENT '当前运行任务数',
    max_parallel_tasks INT DEFAULT 3 COMMENT '最大并行任务数',
    cpu_usage DOUBLE DEFAULT 0 COMMENT 'CPU使用率',
    memory_usage DOUBLE DEFAULT 0 COMMENT '内存使用率',
    version VARCHAR(32) DEFAULT NULL COMMENT '版本',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注',
    last_heartbeat_time DATETIME DEFAULT NULL COMMENT '最后心跳时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'Agent节点表';

-- 迁移任务表（任务模板）
CREATE TABLE IF NOT EXISTS migration_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(128) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(32) NOT NULL DEFAULT 'once' COMMENT '任务类型: once=一次性, scheduled=定时',
    source_cluster_id BIGINT DEFAULT NULL COMMENT '源集群ID',
    source_path VARCHAR(1024) NOT NULL COMMENT '源路径（支持日期表达式，如 ${YYYYMMDD-1}）',
    target_cluster_id BIGINT DEFAULT NULL COMMENT '目标集群ID',
    target_path VARCHAR(1024) NOT NULL COMMENT '目标路径（支持日期表达式）',
    distcp_options VARCHAR(2048) DEFAULT NULL COMMENT 'distcp参数',
    cron_expr VARCHAR(128) DEFAULT NULL COMMENT 'Cron表达式（定时任务）',
    agent_id VARCHAR(64) DEFAULT NULL COMMENT '执行Agent标识',
    status VARCHAR(32) DEFAULT 'draft' COMMENT '任务状态: draft=草稿, online=上线, offline=下线',
    retry_count INT DEFAULT 0 COMMENT '已重试次数（已废弃，迁移至实例）',
    max_retry_count INT DEFAULT 3 COMMENT '最大重试次数',
    priority INT DEFAULT 5 COMMENT '优先级',
    total_files BIGINT DEFAULT 0 COMMENT '总文件数（已废弃，迁移至实例）',
    total_size BIGINT DEFAULT 0 COMMENT '总数据量（已废弃，迁移至实例）',
    completed_files BIGINT DEFAULT 0 COMMENT '已完成文件数（已废弃，迁移至实例）',
    completed_size BIGINT DEFAULT 0 COMMENT '已完成数据量（已废弃，迁移至实例）',
    last_exec_time VARCHAR(32) DEFAULT NULL COMMENT '最近执行时间',
    next_exec_time VARCHAR(32) DEFAULT NULL COMMENT '下次执行时间（已废弃）',
    complete_time VARCHAR(32) DEFAULT NULL COMMENT '完成时间（已废弃，迁移至实例）',
    error_msg TEXT DEFAULT NULL COMMENT '错误信息（已废弃，迁移至实例）',
    alert_enabled TINYINT(1) DEFAULT 0 COMMENT '是否启用告警',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '迁移任务表（任务模板）';

-- 任务实例表
CREATE TABLE IF NOT EXISTS task_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_task_id BIGINT NOT NULL COMMENT '父任务ID（migration_task.id）',
    instance_name VARCHAR(256) NOT NULL COMMENT '实例名称（任务名称_yyyyMMddHHmmss）',
    source_cluster_id BIGINT DEFAULT NULL COMMENT '源集群ID',
    source_path VARCHAR(1024) NOT NULL COMMENT '源路径（已解析日期表达式）',
    target_cluster_id BIGINT DEFAULT NULL COMMENT '目标集群ID',
    target_path VARCHAR(1024) NOT NULL COMMENT '目标路径（已解析日期表达式）',
    distcp_options VARCHAR(2048) DEFAULT NULL COMMENT 'distcp参数',
    agent_id VARCHAR(64) DEFAULT NULL COMMENT '执行Agent标识',
    status VARCHAR(32) DEFAULT 'pending' COMMENT '实例状态: pending=待执行, dispatching=派发中, running=运行中, retrying=重试中, success=已完成, failed=失败, stopped=已停止, killed=已终止',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT DEFAULT 3 COMMENT '最大重试次数',
    priority INT DEFAULT 5 COMMENT '优先级',
    total_files BIGINT DEFAULT 0 COMMENT '总文件数',
    total_size BIGINT DEFAULT 0 COMMENT '总数据量',
    completed_files BIGINT DEFAULT 0 COMMENT '已完成文件数',
    completed_size BIGINT DEFAULT 0 COMMENT '已完成数据量',
    last_exec_time VARCHAR(32) DEFAULT NULL COMMENT '启动时间',
    complete_time VARCHAR(32) DEFAULT NULL COMMENT '完成时间',
    error_msg TEXT DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_task_id (parent_task_id),
    INDEX idx_status (status)
) COMMENT '任务实例表';

-- 任务日志表
CREATE TABLE IF NOT EXISTS task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务实例ID（task_instance.id）',
    log_level VARCHAR(16) DEFAULT 'INFO' COMMENT '日志级别: INFO/WARN/ERROR',
    content TEXT NOT NULL COMMENT '日志内容',
    line_number INT DEFAULT NULL COMMENT '行号',
    log_source VARCHAR(64) DEFAULT 'agent' COMMENT '日志来源',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_create_time (create_time)
) COMMENT '任务日志表';

-- 校验结果表
CREATE TABLE IF NOT EXISTS verify_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务实例ID（task_instance.id）',
    exec_id VARCHAR(64) DEFAULT NULL COMMENT '执行批次ID',
    verify_status VARCHAR(32) DEFAULT 'pending' COMMENT '校验状态: pending=待校验, match=一致, mismatch=不一致, error=异常',
    source_file_count BIGINT DEFAULT 0 COMMENT '源端文件数',
    target_file_count BIGINT DEFAULT 0 COMMENT '目标端文件数',
    source_total_size BIGINT DEFAULT 0 COMMENT '源端数据总量',
    target_total_size BIGINT DEFAULT 0 COMMENT '目标端数据总量',
    diff_file_list TEXT DEFAULT NULL COMMENT '差异文件清单(JSON)',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) COMMENT '校验结果表';

-- 任务操作记录表
CREATE TABLE IF NOT EXISTS task_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务模板ID（migration_task.id）',
    task_name VARCHAR(128) DEFAULT NULL COMMENT '任务名称',
    operation VARCHAR(32) NOT NULL COMMENT '操作类型: create=新建, edit=编辑, online=上线, offline=下线, execute=执行, kill=终止, delete=删除',
    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    detail VARCHAR(512) DEFAULT NULL COMMENT '操作详情',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) COMMENT '任务操作记录表';

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
    real_name VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    status INT DEFAULT 1 COMMENT '状态: 1=正常 0=冻结',
    role VARCHAR(20) DEFAULT 'user' COMMENT '角色: admin/user',
    dept_id BIGINT DEFAULT NULL COMMENT '部门ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '系统用户表';

-- 默认管理员账号 (admin / admin123)
INSERT INTO sys_user (username, password, real_name, email, status, role)
SELECT 'admin', '$2a$10$z9v3WlbC3vCqU/V0o72osO6/CWW6pwN0j4pLKM7SOaRx99Uf1cwGy', '管理员', 'admin@localhost', 1, 'admin'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- AI模型配置表
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL COMMENT '配置名称',
    base_url VARCHAR(500) NOT NULL COMMENT 'API地址',
    api_key VARCHAR(500) NOT NULL COMMENT 'API Key',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度',
    max_tokens INT DEFAULT 2000 COMMENT '最大Token数',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认',
    status INT DEFAULT 1 COMMENT '状态: 1=启用 0=禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'AI模型配置表';

-- AI对话会话表
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL DEFAULT 'New Conversation' COMMENT '会话标题',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    config_id BIGINT DEFAULT NULL COMMENT 'AI配置ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) COMMENT 'AI对话会话表';

-- AI对话消息表
CREATE TABLE IF NOT EXISTS ai_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色: user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id)
) COMMENT 'AI对话消息表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    login_ip VARCHAR(128) DEFAULT NULL COMMENT '登录IP',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    INDEX idx_username (username),
    INDEX idx_create_time (create_time)
) COMMENT '登录日志表';

-- 告警配置表
CREATE TABLE IF NOT EXISTS alert_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(32) NOT NULL UNIQUE COMMENT '告警类型: task_failed=任务失败, agent_offline=Agent离线, agent_online=Agent上线, verify_mismatch=校验不一致',
    enabled TINYINT DEFAULT 0 COMMENT '是否启用: 1=启用 0=禁用',
    remark VARCHAR(128) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '告警配置表';

-- 告警webhook配置表
CREATE TABLE IF NOT EXISTS alert_webhook (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_type VARCHAR(32) NOT NULL UNIQUE COMMENT 'webhook类型: wechat=企业微信, dingtalk=钉钉',
    webhook VARCHAR(500) DEFAULT '' COMMENT 'webhook地址',
    enabled TINYINT DEFAULT 0 COMMENT '是否启用: 1=启用 0=禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '告警webhook配置表';

-- 初始化默认告警配置
INSERT INTO alert_config (alert_type, enabled, remark)
SELECT 'task_failed', 0, '任务失败告警'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM alert_config WHERE alert_type = 'task_failed');

INSERT INTO alert_config (alert_type, enabled, remark)
SELECT 'agent_offline', 0, 'Agent离线告警'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM alert_config WHERE alert_type = 'agent_offline');

INSERT INTO alert_config (alert_type, enabled, remark)
SELECT 'agent_online', 0, 'Agent上线告警'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM alert_config WHERE alert_type = 'agent_online');

INSERT INTO alert_config (alert_type, enabled, remark)
SELECT 'verify_mismatch', 0, '校验不一致告警'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM alert_config WHERE alert_type = 'verify_mismatch');

-- 初始化默认webhook配置
INSERT INTO alert_webhook (webhook_type, webhook, enabled)
SELECT 'wechat', '', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM alert_webhook WHERE webhook_type = 'wechat');

INSERT INTO alert_webhook (webhook_type, webhook, enabled)
SELECT 'dingtalk', '', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM alert_webhook WHERE webhook_type = 'dingtalk');
