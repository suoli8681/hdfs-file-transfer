# AGENTS.md - HDFS 文件迁移平台

> 本文件供 AI 编程助手阅读，帮助理解项目架构与约定，避免重复 token 消耗和误操作。

## 项目概述

HDFS 文件迁移平台，用于在不同 Hadoop 集群间通过 `hadoop distcp` 进行数据迁移。
采用 Server-Agent 架构：Server 负责任务调度和管理（运行在 Windows/任意 OS），Agent 负责执行 distcp 命令（运行在 Linux + Hadoop 环境）。

内置 AI 助手功能（OpenAI 兼容 API），支持自然语言查询迁移数据。

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| JDK | Java | **1.8**（严禁使用 JDK 9+ API） |
| 后端框架 | Spring Boot | 2.7.18 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 5.7/8.0 |
| 认证 | Spring Security + JWT (jjwt 0.9.1) | |
| API 文档 | Springdoc OpenAPI | 1.7.0 |
| 前端框架 | Vue 3 + Vite 5 + Element Plus 2.5 | |
| 图表 | ECharts 5 + vue-echarts（依赖已引入，暂未使用） | |
| 构建工具 | Maven 3.6+ | |

## JDK 8 兼容性红线

**禁止使用以下 API（均为 JDK 9+）：**
- `Process.pid()` → 用 `ManagementFactory.getRuntimeMXBean().getName().split("@")[0]`
- `OperatingSystemMXBean.getCpuLoad()` → 用 `getSystemCpuLoad()`
- `var` 关键字
- `List.of()` / `Set.of()` / `Map.of()`
- `String.isBlank()` / `.strip()` / `.repeat()` / `.lines()`
- `Files.readString()` / `Files.writeString()`
- `InputStream.readAllBytes()`
- `Optional.isEmpty()`
- `jakarta.servlet.*` → 用 `javax.servlet.*`（Spring Boot 2.x）
- `@EnableMethodSecurity` → 用 `@EnableGlobalMethodSecurity(prePostEnabled = true)`
- `requestMatchers()` → 用 `antMatchers()`
- `authorizeHttpRequests()` → 用 `authorizeRequests()`

## 模块结构

```
hdfs-file-transfer/          (parent pom)
├── common/                  (共享 DTO + Enum，被 server 和 agent 依赖)
├── server/                  (后端服务，端口 8080)
├── agent/                   (Agent 服务，部署到 Linux，端口 8081)
├── web/                     (Vue3 前端，端口 3000)
├── deploy/                  (构建产物：server/agent/web 部署包)
├── build.ps1                (Windows 一键构建脚本)
└── build.sh                 (Linux 一键构建脚本)
```

## 数据库

- 地址：`192.168.1.125:3306`
- 库名：`hdfs_transfer`
- 初始化脚本：`server/src/main/resources/schema.sql`
- 共 14 张表：

| 表名 | 说明 |
|------|------|
| `cluster_config` | 集群配置（名称、NameNode 地址、HDFS 用户等） |
| `agent_node` | Agent 节点（状态、心跳、CPU/内存） |
| `migration_task` | 迁移任务模板（源/目标路径、状态、告警开关等） |
| `task_instance` | 任务实例（每次执行生成的实例，含进度、重试等） |
| `task_log` | 任务执行日志（来自 Agent 的 distcp 输出） |
| `verify_result` | 校验结果（文件数/数据量对比、差异文件列表） |
| `sys_user` | 系统用户（BCrypt 密码），schema.sql 含默认 admin 用户 |
| `task_operation_log` | 任务操作记录（创建/编辑/上线/下线/执行/终止/删除，含操作人） |
| `ai_config` | AI 模型配置（API 地址、密钥、模型名、温度等） |
| `ai_conversation` | AI 对话会话（标题、用户名、配置 ID） |
| `ai_message` | AI 对话消息（会话 ID、角色、内容） |
| `login_log` | 登录日志（用户名、登录 IP、登录时间） |
| `alert_config` | 告警类型配置（告警类型、启用状态、备注） |
| `alert_webhook` | 告警通知渠道（企业微信/钉钉 webhook 地址、启用状态） |

**注意：** schema.sql 需手动执行（`mysql -h <host> -u root -p < schema.sql`），Spring Boot 不会自动运行。schema.sql 含默认管理员账号 `admin / admin123`（BCrypt 加密）。

**已知配置问题：** `application.yml` 中 MyBatis-Plus 配置了 `logic-delete-field: deleted`，但所有表均无 `deleted` 字段，也无 Entity 使用 `@TableLogic` 注解。此配置当前无效但无害。

## Common 模块（com.hdfs.transfer.common）

共享模块，被 server 和 agent 依赖，无 Spring Boot 启动类。

### DTO（6 个）
| 类名 | 用途 |
|------|------|
| `ApiResponse<T>` | 统一响应封装（code/message/data），静态方法 `success()`/`error()` |
| `TaskDTO` | 任务数据传输（taskId/taskName/taskType/源目标集群路径/distcpOptions/agentId/alertEnabled） |
| `TaskProgressDTO` | 任务进度（totalSize/completedSize/progressPercent/totalFiles/completedFiles/status） |
| `HeartbeatDTO` | Agent 心跳（agentId/status/cpuUsage/memoryUsage/taskProgressList） |
| `LogEntryDTO` | 日志条目（taskId/level/content/timestamp） |
| `VerifyResultDTO` | 校验结果（verifyStatus/文件数/数据量/diffFiles/diffDetails），内含 `DiffFileInfo` |

### Enum（4 个）
| 类名 | 值 |
|------|------|
| `TaskStatusEnum` | pending, running, success, failed, stopped, retrying |
| `TaskTypeEnum` | once, scheduled |
| `AgentStatusEnum` | online, offline, busy |
| `VerifyStatusEnum` | pending, match, mismatch, error |

**注意：** 前端实际使用 9 种任务状态（额外有 `draft`/`dispatching`/`killed`），但 `TaskStatusEnum` 仅定义了 6 种。新增状态仅在数据库 status 字段和前端使用，未同步到枚举类。

## Server 模块（com.hdfs.transfer.server）

### 分层结构
```
api/          → AuthController, OpenApiController（对外 API）
controller/   → AgentController, AiChatController, AlertConfigController,
                ClusterConfigController, DashboardController, LogController,
                LoginLogController, MigrationTaskController, SysUserController,
                TaskInstanceController, TaskOperationLogController, VerifyController
monitor/      → AgentReportController（Agent 上报专用，免认证）
service/      → 业务逻辑层（13 个 Service）
entity/       → MyBatis-Plus 实体（14 个 Entity）
mapper/       → MyBatis-Plus Mapper 接口（14 个 Mapper，仅 SysUserMapper 有 XML）
security/     → SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
scheduler/    → TaskDispatchJob(10s), AgentMonitorJob(30s), LogCleanupJob(每天3点)
config/       → MyBatisPlusConfig, MetaObjectHandlerConfig, ScheduleConfig, SwaggerConfig
alert/        → AlertService（钉钉/企微告警通知）
```

### 认证规则
- 公开接口（permitAll）：`/api/auth/**`、`/api/report/**`、`/api/tasks/dispatch`、`/api/tasks/*/status`、`/swagger-ui/**`、`/v3/api-docs/**`
- 其他所有接口需 JWT 认证
- Agent 上报心跳/日志/校验结果走 `/api/report/**`，无需 token
- `/open-api/**` 路径未加入 permitAll，实际需要 JWT 认证
- JWT token 也可通过 `?token=` 查询参数传递（用于 SSE EventSource）
- JWT 默认密钥：`hdstransfer-secret-key-2024`，默认过期时间：24h

### 关键业务逻辑

**任务状态流转：**
```
draft → online → execute → task_instance(pending → dispatching → running → success/failed)
  ↓                ↓                                                    ↓
offline      下线后不再生成新实例                              failed → retrying → running → ...
                                                                  ↓
                                                           stopped / killed（强制终止 + HDFS 清理）
```

**任务创建初始状态为 `draft`**，用户点击"上线"后 `online()` 将状态改为 `online`，用户点击"执行"后生成任务实例（`task_instance`，初始 `pending`），随后 `TaskDispatchJob` 分配为 `dispatching`，Agent 拉取后 CAS 改为 `running`。

**任务操作按钮展示规则（前端实际实现）：**
| 状态 | 按钮 |
|------|------|
| draft | 编辑、上线、操作记录、删除 |
| online | 执行、查看实例、操作记录、下线 |
| offline | 上线、编辑、查看实例、操作记录、删除 |

**启动防重复：** 后端 `execute()` 校验状态，running/dispatching/retrying 抛异常拒绝。前端用 `executingIds`/`killingIds` 数组禁用按钮。

**任务名称唯一：** `add()` 时校验，重复抛 `RuntimeException`。

**操作记录：** `MigrationTaskService` 在 create/edit/online/offline/execute/kill/delete 时自动调用 `operationLogService.record()`，操作人从 `SecurityContext` 获取。

**强制终止：** `forceKill()` 发送 HTTP POST 到 Agent `http://{host}:{port}/api/agent/task/{taskId}/kill`，Agent 执行 `destroyForcibly()` + `hadoop fs -rm -r` 清理目标端残留数据。

**卡住任务恢复：** `TaskDispatchService.recoverStuckDispatchingTasks()` 将超过 `dispatch-timeout-seconds`（默认 120s）仍处于 `dispatching` 的任务重置为 `pending`。

### AI 助手功能

- **AiChatController**（`/api/ai/**`）：会话管理、SSE 流式聊天、模型配置 CRUD
- **AiChatService**：调用 OpenAI 兼容 API，支持 Function Calling（`query_migration_data`）
  - 查询类型：`task_stats`、`task_list`、`agent_status`、`cluster_list`、`task_detail`
  - 最大递归深度 5 轮（AI 可连续多次调用工具）
  - SSE 事件类型：`content`（文本片段）、`tool`（工具调用提示）、`error`、`done`
- **AiConfigEntity**：支持多模型配置，可设置默认、测试连通性
- **AiConversationEntity / AiMessageEntity**：对话历史持久化
- 前端 `AiChat.vue` 使用 EventSource 流式接收，token 通过 `?token=` 查询参数传递

### 用户管理

- **SysUserController**（`/api/users/**`）：分页查询、创建、编辑、启用/冻结、重置密码
- **SysUserService** 实现 `UserDetailsService`，BCrypt 加密密码
- 密码重置校验：6-8 字符，须含字母和数字
- 前端用户管理页 `/users` 仅 admin 角色可见（路由守卫 + 菜单 `v-if`）
- `Layout.vue` 启动时调 `GET /users/current` 检查角色，存入 localStorage

### 告警通知

- **AlertService**：任务失败、Agent 上线/离线、校验不一致时触发
- 支持钉钉（Markdown 消息）和企业微信（文本消息），均通过 RestTemplate 发送
- 配置存储在数据库 `alert_config`（告警类型开关）和 `alert_webhook`（webhook 地址）表中，非 `application.yml`
- **AlertConfigController**（`/api/alert-config`）：查询配置、更新告警类型/渠道、测试 webhook
- 告警类型：`task_failed`、`agent_offline`、`agent_online`、`verify_mismatch`，可独立启用/禁用
- 通知渠道：企业微信（`wechat`）、钉钉（`dingtalk`），可独立配置 webhook 地址和启用状态
- 任务级别告警开关：`migration_task.alert_enabled` 字段控制单个任务是否告警，新建任务时默认开启
- Agent 上线告警触发时机：首次注册、从 offline 恢复（心跳检测到状态转换）

### 定时任务

| 任务 | 类 | 间隔 | 说明 |
|------|------|------|------|
| TaskDispatchJob | scheduler/ | 10s | 分配 pending/retrying 任务给 Agent |
| AgentMonitorJob | scheduler/ | 30s | 心跳超时（60s）标记 Agent 离线 |
| LogCleanupJob | scheduler/ | 每天 3:00 | 清理过期日志（默认 30 天） |

### 配置类
| 类 | 说明 |
|------|------|
| MyBatisPlusConfig | 分页插件（PaginationInnerInterceptor, MySQL） |
| MetaObjectHandlerConfig | 自动填充 createTime/updateTime |
| ScheduleConfig | TaskScheduler 线程池（poolSize=4） |
| SwaggerConfig | OpenAPI 文档配置 |

### Server 配置项（application.yml）

```yaml
hdfs.transfer:
  agent-heartbeat-timeout: 60      # Agent 心跳超时秒数
  max-concurrent-tasks: 10         # 最大并发任务数
  default-retry-count: 3           # 默认重试次数（注：代码中硬编码为3，未读此配置）
  task-log-retention-days: 30      # 日志保留天数
  dispatch-timeout-seconds: 120    # 派发超时秒数

# JWT（默认值在 @Value 注解中）
# jwt.secret = hdstransfer-secret-key-2024
# jwt.expiration = 86400000 (24h)
```

## Agent 模块（com.hdfs.transfer.agent）

### 核心流程
```
HeartbeatService(10s) → 上报心跳 + CPU/内存 + 任务进度
TaskPollerService(15s) → 拉取任务 → TaskExecutionManager.executeTask()
  0. PathExpressionResolver.resolve() → 替换 ${YYYY-MM-DD+N} 日期占位符
  1. PreCheckService.preCheck() → 检查 Hadoop 环境/源路径/目标路径，返回 PreCheckResult（含错误信息）
  2. getSourceStats() → hadoop fs -count + du -s 获取总量
  3. ShellScriptGenerator.generateDistcpScript() → 生成 bash 脚本
  4. ShellProcessManager.startScript() → 执行 bash
  5. 解析 stdout → parseProgress() → 匹配 "map X% reduce Y%" / "Copied N files"
  6. reportThrottled() → 每 20 行或 10s 上报一次进度
  7. 成功 → DataVerifier.verify() → hadoop fs -count 比对源/目标
  8. 失败 → RetryHandler.handleRetry() → 加 -update 重试（指数退避 5s*N）
```

### 包结构
```
communication/ → HeartbeatService, ServerCommunicator, TaskPollerService
executor/      → TaskExecutionManager, ShellScriptGenerator, ShellProcessManager, PathExpressionResolver
precheck/      → PreCheckService
verify/        → DataVerifier
retry/         → RetryHandler
monitor/       → LogCollector
config/        → AgentConfig, ScheduleConfig, TaskExecutorConfig
controller/    → AgentTaskController（stop/kill REST 端点）
```

### 关键配置（application.yml）
- `hdfs.transfer.server.base-url` → Server 地址
- `hdfs.transfer.server.heartbeat-interval` → 心跳间隔（默认 10s）
- `hdfs.transfer.agent.hadoop-home` → Hadoop 安装路径（默认 /opt/hadoop）
- `hdfs.transfer.agent.work-dir` → 工作目录（默认 /opt/hdfs-transfer/agent/work）
- `hdfs.transfer.agent.max-parallel-tasks` → 最大并行任务数（默认 3）
- `hdfs.transfer.agent.retry-max-count` → 最大重试次数（默认 3）
- `hdfs.transfer.agent.task-timeout-hours` → 任务超时小时数（0=不超时）

### 循环依赖处理
`TaskExecutionManager` ↔ `RetryHandler` 互相注入，用 `@Lazy` 在 RetryHandler 构造器上打破循环。同时 `application.yml` 中 `spring.main.allow-circular-references: true`。

### 日期占位符
`PathExpressionResolver.resolve()` 支持在路径中使用 `${YYYY-MM-DD}`、`${YYYY-MM-DD+1}`（后N天）等占位符，在执行前替换为实际日期。

### distcp 验证逻辑
distcp 复制目录时保留目录名：`distcp /src/dir /dst/` → 创建 `/dst/dir/`。
验证脚本自动计算 `ACTUAL_TGT_PATH = targetPath/basename(sourcePath)`。
校验脚本执行 `hadoop fs -count` + `hadoop fs -du -s` 比对，不一致时用 `diff` 列出差异文件。

### 任务超时
`task-timeout-hours > 0` 时启动守护线程，超时后 `destroyForcibly()` 杀死进程。默认 0 不超时。

### Agent REST 端点（AgentTaskController）
- `POST /api/agent/task/{taskId}/stop` → 优雅停止（`process.destroy()`）
- `POST /api/agent/task/{taskId}/kill` → 强制终止 + HDFS 残留清理

## 前端模块（web/）

### 页面路由
| 路径 | 页面 | 说明 |
|------|------|------|
| /login | Login.vue | 登录页（公开） |
| /dashboard | Dashboard.vue | 监控大盘（统计卡片 + 最近任务） |
| /clusters | ClusterList.vue | 集群管理（CRUD + 连通性测试） |
| /clusters/add, /:id/edit | ClusterForm.vue | 集群表单 |
| /tasks | TaskList.vue | 迁移任务（CRUD + 上线/下线/执行/终止 + 日志/校验/操作记录弹窗） |
| /tasks/add, /:id/edit | TaskForm.vue | 任务表单（含告警开关） |
| /task-instances | TaskInstanceList.vue | 任务实例（分页查询 + 终止 + 日志/校验弹窗） |
| /agents | AgentList.vue | Agent 管理（列表 + 状态） |
| /verify | VerifyResult.vue | 校验结果（分页列表） |
| /users | UserList.vue | 用户管理（仅 admin 可见） |
| /login-logs | LoginLogList.vue | 登录日志（仅 admin 可见） |
| /ai-chat | AiChat.vue | AI 助手（SSE 流式聊天） |
| /ai-config | AiConfig.vue | AI 模型配置（CRUD + 测试 + 默认设置） |
| /alert-config | AlertConfig.vue | 告警配置（仅 admin 可见） |

### axios 拦截器（api/index.js）
- baseURL：`/api`，timeout 30s
- 请求拦截：自动添加 `Authorization: Bearer {token}`（`skipAuth` 配置项跳过）
- 响应拦截：提取 `response.data.data`（ApiResponse 的 data 字段），401/403 清除 localStorage 跳转登录页

### 路由守卫（router/index.js）
- `/login` 公开，其他需 token
- 已登录访问 `/login` 自动跳转 `/`
- `/users`、`/login-logs`、`/alert-config` 仅 admin 角色（localStorage `role === 'admin'`）可访问

### 全局工具函数（utils/index.js）
- `statusType(status)` → Element Plus tag type（支持 draft/pending/running/success/failed/stopped/killed/retrying/dispatching）
- `statusLabel(status)` → 中文标签
- `formatSize(bytes)` → 人类可读大小（B/KB/MB/GB/TB）

### Vite 配置
- 开发端口 3000
- API 代理：`/api` → `http://localhost:8080`

## 构建与部署

### 一键构建（build.ps1 / build.sh）
脚本依次执行：构建 Server → 构建 Agent → 创建 deploy/ 目录 → 拷贝 jar/yml/sh → 构建前端 → 拷贝 dist → 生成 nginx.conf

产物结构：
```
deploy/
├── server/   (server-1.0.0.jar + application.yml + start.sh + stop.sh)
├── agent/    (agent-1.0.0.jar + application.yml + start.sh + stop.sh)
└── web/      (dist/* + nginx.conf)
```

### 单独构建
```bash
# 后端（需先停掉运行中的 java 进程）
mvn package -pl server -am -DskipTests -q
# Agent
mvn package -pl agent -am -DskipTests -q
# 前端
cd web && npm install && npm run dev  # 开发
cd web && npm run build                # 生产
```

### 启动 Server（Windows）
```powershell
# 必须用 UseShellExecute=true 启动，否则进程会被工具杀掉
$w = New-Object System.Diagnostics.ProcessStartInfo
$w.FileName = "C:\Program Files\Java\jdk1.8.0_231\bin\javaw.exe"
$w.Arguments = "-jar G:\suoli\hdfs-file-transfer\server\target\server-1.0.0.jar"
$w.WorkingDirectory = "G:\suoli\hdfs-file-transfer\server"
$w.UseShellExecute = $true
$w.WindowStyle = "Hidden"
[System.Diagnostics.Process]::Start($w)
```

### 部署 Agent（Linux）
```bash
# 上传 agent/target/deploy/ 目录到 Linux
scp -r agent/target/deploy/ user@linux:/opt/hdfs-transfer/agent/
# 修改 application.yml 中的 server.base-url 和 hadoop-home
# dos2unix start.sh stop.sh
./start.sh   # nohup 启动
./stop.sh    # 优雅停止
```

### 数据库初始化
```bash
mysql -h <db_host> -u root -p < server/src/main/resources/schema.sql
```
schema.sql 含 `CREATE DATABASE` + 14 张表 + 默认 admin 用户（admin/admin123）。
所有表用 `CREATE TABLE IF NOT EXISTS`，可安全重复执行。

### 数据库变更
新增字段/表时手动执行 ALTER TABLE，schema.sql 仅用于初始建表。

## 常见陷阱

1. **YAML 缩进**：`spring.datasource` 必须在 `spring:` 下，不能误缩进到 `logging:` 下
2. **fat jar 锁定**：Server 运行时 `mvn package` 会失败，需先 `Stop-Process java`
3. **target 目录清理**：`mvn clean` 会删除 `agent/target/deploy/`，需重新写入 start.sh/stop.sh/application.yml
4. **中文编码**：Windows PowerShell 编译 Java 时需 `-encoding UTF-8`，写文件用 `[System.IO.File]::WriteAllText` 避免 BOM
5. **MySQL 驱动**：parent pom 用 `mysql:mysql-connector-java:8.0.30`，不要用 `mysql-connector-j`（名称变更会导致驱动找不到）
6. **Springfox→Springdoc**：已用 springdoc-openapi-ui 1.7.0 替换 Springfox，需 `spring.mvc.pathmatch.matching-strategy: ant_path_matcher`
7. **Quartz 依赖**：server pom 引入了 `spring-boot-starter-quartz`，`application.yml` 配了 `initialize-schema: always`，但实际调度用 Spring `@Scheduled`，Quartz 表会自动创建但未使用
8. **deploy 目录清理**：`build.ps1`/`build.sh` 每次会删除整个 `deploy/` 重建
9. **Agent 循环引用**：`TaskExecutionManager` ↔ `RetryHandler` 需 `@Lazy` + `allow-circular-references: true`
10. **logic-delete 配置**：`application.yml` 配了 `logic-delete-field: deleted` 但无表有此字段，也无 Entity 用 `@TableLogic`，配置无效但无害
