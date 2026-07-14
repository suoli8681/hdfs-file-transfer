# HDFS 文件迁移平台

## 项目简介

HDFS 文件迁移平台，用于在不同 Hadoop 集群间通过 `hadoop distcp` 进行数据迁移。采用 Server-Agent 架构，提供 Web 管理界面，支持任务调度、进度监控、数据校验、告警通知、用户管理，并内置 AI 助手支持自然语言查询迁移数据。

## 系统架构

```
┌──────────────┐     ┌──────────────────────────────────────────┐     ┌──────────────────┐
│   Web 前端   │     │              Server (后端)               │     │   Agent (Linux)   │
│  Vue3+EPlus  │────→│  Spring Boot 2.7 + MyBatis-Plus + MySQL │←────│  Spring Boot +    │
│  port:3000   │     │  port:8080                               │     │  Hadoop Client   │
│              │     │                                          │     │  port:8081        │
└──────────────┘     └──────────────────────────────────────────┘     └──────────────────┘
                            │                                                    │
                            ▼                                                    ▼
                      ┌───────────┐                                    ┌───────────────────┐
                      │  MySQL    │                                    │  Hadoop Cluster   │
                      │  hdfs_    │                                    │  (hadoop distcp)  │
                      │  transfer │                                    └───────────────────┘
                      └───────────┘
```

### 组件说明

| 组件 | 技术栈 | 部署位置 | 端口 |
|------|--------|----------|------|
| **Web 前端** | Vue 3 + Vite 5 + Element Plus 2.5 + ECharts 5 | 开发机 | 3000 |
| **Server** | Spring Boot 2.7.18 + MyBatis-Plus 3.5.5 + Spring Security + JWT | 任意 OS（需 JDK 1.8） | 8080 |
| **Agent** | Spring Boot 2.7.18（轻量，无数据库） | Linux（需 Hadoop 环境） | 8081 |
| **MySQL** | MySQL 5.7/8.0 | 数据库服务器 | 3306 |

### 数据流

1. 用户通过 Web 界面创建迁移任务，选择源集群、目标集群、路径、Agent
2. Server 调度器（10s 间隔）将 `pending` 状态的任务分配给指定 Agent，状态变为 `dispatching`
3. Agent 轮询（15s 间隔）拉取 `dispatching` 任务，CAS 改为 `running`，执行 `hadoop distcp`
4. Agent 实时上报进度（心跳 10s 间隔 + 状态推送）
5. distcp 完成后 Agent 自动执行数据校验（文件数 + 数据量比对）
6. 校验结果和操作记录入库，前端可查看

## 功能特性

- **任务管理**：创建/编辑/启动/停止/强制终止迁移任务，支持任务优先级
- **进度监控**：实时展示 distcp 复制进度（文件数、数据量、百分比）
- **数据校验**：distcp 完成后自动比对源/目标端文件数和数据量，列出差异文件
- **自动重试**：任务失败后自动加 `-update` 参数重试（指数退避，最多 3 次）
- **集群管理**：配置多个 Hadoop 集群，支持连通性测试
- **Agent 管理**：监控 Agent 节点状态、CPU/内存使用率
- **用户管理**：系统用户增删改查、启用/冻结、密码重置（仅 admin）
- **AI 助手**：基于 OpenAI 兼容 API 的自然语言对话，支持 Function Calling 查询迁移数据
- **告警通知**：任务失败、Agent 离线、校验不一致时推送钉钉/企业微信告警
- **操作审计**：记录任务创建/编辑/启动/停止/终止/删除操作及操作人
- **日期占位符**：支持路径中使用 `${YYYY-MM-DD}`、`${YYYY-MM-DD+1}` 等动态日期

## 模块结构

```
hdfs-file-transfer/
├── pom.xml                  (父 POM，统一依赖版本管理)
├── AGENTS.md                (AI 编程助手指南)
├── build.ps1                (Windows 一键构建脚本)
├── build.sh                 (Linux 一键构建脚本)
├── common/                  (共享模块：DTO + Enum)
│   └── src/main/java/com/hdfs/transfer/common/
│       ├── dto/             (ApiResponse, HeartbeatDTO, TaskDTO, TaskProgressDTO,
│       │                    VerifyResultDTO, LogEntryDTO)
│       └── enums/           (TaskStatusEnum, TaskTypeEnum, AgentStatusEnum,
│                            VerifyStatusEnum)
├── server/                  (后端服务)
│   └── src/main/java/com/hdfs/transfer/server/
│       ├── api/             (AuthController, OpenApiController)
│       ├── controller/      (AgentController, AiChatController, ClusterConfigController,
│       │                    DashboardController, LogController, MigrationTaskController,
│       │                    SysUserController, TaskOperationLogController, VerifyController)
│       ├── service/         (10 个 Service)
│       ├── entity/          (10 个 Entity)
│       ├── mapper/          (10 个 Mapper，仅 SysUserMapper 有 XML)
│       ├── security/        (SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter)
│       ├── scheduler/       (TaskDispatchJob, AgentMonitorJob, LogCleanupJob)
│       ├── monitor/         (AgentReportController - Agent 上报专用)
│       ├── config/          (MyBatisPlusConfig, MetaObjectHandlerConfig,
│       │                    ScheduleConfig, SwaggerConfig)
│       └── alert/           (AlertService - 钉钉/企微告警)
├── agent/                   (Agent 服务)
│   └── src/main/java/com/hdfs/transfer/agent/
│       ├── communication/   (HeartbeatService, ServerCommunicator, TaskPollerService)
│       ├── executor/        (TaskExecutionManager, ShellScriptGenerator,
│       │                    ShellProcessManager, PathExpressionResolver)
│       ├── precheck/        (PreCheckService)
│       ├── verify/          (DataVerifier)
│       ├── retry/           (RetryHandler)
│       ├── monitor/         (LogCollector)
│       ├── controller/      (AgentTaskController - stop/kill 端点)
│       └── config/          (AgentConfig, ScheduleConfig, TaskExecutorConfig)
├── web/                     (前端)
│   └── src/
│       ├── api/             (11 个 API 模块)
│       ├── components/      (Layout.vue)
│       ├── views/           (13 个页面)
│       ├── composables/     (auth.js - 未实际使用)
│       ├── router/          (Vue Router + 认证守卫)
│       └── utils/           (工具函数)
└── deploy/                  (构建产物，由 build.ps1/build.sh 生成)
    ├── server/              (jar + application.yml + start.sh + stop.sh)
    ├── agent/               (jar + application.yml + start.sh + stop.sh)
    └── web/                 (dist + nginx.conf)
```

## 数据库设计

数据库名：`hdfs_transfer`，共 10 张表：

| 表名 | 说明 |
|------|------|
| `cluster_config` | 集群配置（名称、NameNode 地址、HDFS 用户等） |
| `agent_node` | Agent 节点（状态、心跳、CPU/内存） |
| `migration_task` | 迁移任务（源/目标路径、状态、进度、时间） |
| `task_log` | 任务执行日志（来自 Agent 的 distcp 输出） |
| `verify_result` | 校验结果（文件数/数据量对比、差异文件列表） |
| `sys_user` | 系统用户（BCrypt 密码），含默认 admin 用户 |
| `task_operation_log` | 任务操作记录（创建/编辑/启动/停止/终止/删除，含操作人） |
| `ai_config` | AI 模型配置（API 地址、密钥、模型名、温度等） |
| `ai_conversation` | AI 对话会话（标题、用户名、配置 ID） |
| `ai_message` | AI 对话消息（会话 ID、角色、内容） |

## API 概览

### 认证 API（`/api/auth/**`，公开）
- `POST /auth/login` — 登录，返回 JWT token
- `POST /auth/register` — 注册
- `GET /auth/info` — 获取当前用户信息
- `PUT /auth/profile` — 修改个人信息
- `POST /auth/password` — 修改密码

### 任务 API（`/api/tasks/**`）
- `GET /tasks/page` — 分页查询任务
- `GET /tasks/{id}` — 获取任务详情
- `POST /tasks` — 创建任务
- `PUT /tasks` — 编辑任务
- `POST /tasks/{id}/start` — 启动任务
- `POST /tasks/{id}/stop` — 停止任务
- `POST /tasks/{id}/force-kill` — 强制终止任务
- `GET /tasks/dispatch` — Agent 拉取任务（公开）
- `POST /tasks/{id}/status` — Agent 上报状态/进度（公开）
- `DELETE /tasks/{id}` — 删除任务

### 集群 API（`/api/clusters/**`）
- 分页查询、列表、详情、CRUD、连通性测试

### Agent API（`/api/agents/**`）
- `GET /agents/list` — Agent 列表
- `POST /agents/register` — Agent 注册
- `POST /agents/heartbeat` — Agent 心跳

### Agent 上报 API（`/api/report/**`，公开）
- `POST /report/heartbeat` — 心跳上报
- `POST /report/logs` — 日志批量上传
- `POST /report/verify` — 校验结果上报

### 用户管理 API（`/api/users/**`）
- 分页查询、当前用户、创建、编辑、启用/冻结、重置密码

### AI 助手 API（`/api/ai/**`）
- 会话 CRUD、SSE 流式聊天（`GET /ai/chat`）、模型配置 CRUD、测试连通性、设置默认

### 其他 API
- **日志**（`/api/logs/**`）：分页查询、批量上传
- **校验**（`/api/verify/**`）：分页查询、最新结果
- **操作记录**（`/api/task-logs/**`）：分页查询、按任务查询
- **仪表盘**（`/api/dashboard/**`）：总览统计、最近任务
- **Open API**（`/open-api/**`）：外部集成接口（注：需 JWT 认证）

## 部署说明

### 环境要求

| 环境 | 要求 |
|------|------|
| JDK | 1.8（Server 和 Agent 均需） |
| Maven | 3.6+ |
| Node.js | 18+ |
| MySQL | 5.7 / 8.0 |
| Hadoop | 2.x / 3.x（仅 Agent 需要） |

### 1. 数据库初始化

```bash
# 执行 schema.sql 建表 + 创建默认管理员
mysql -h <db_host> -u root -p < server/src/main/resources/schema.sql
```

schema.sql 会创建数据库 `hdfs_transfer`、10 张表，并插入默认管理员账号（admin/admin123）。
所有表使用 `CREATE TABLE IF NOT EXISTS`，可安全重复执行。

### 2. 配置修改

**Server** (`server/src/main/resources/application.yml`)：
```yaml
spring:
  datasource:
    url: jdbc:mysql://<DB_HOST>:3306/hdfs_transfer?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: <DB_USER>
    password: <DB_PASSWORD>
```

**Agent** (`agent/src/main/resources/application.yml` 或部署目录 `application.yml`)：
```yaml
hdfs:
  transfer:
    server:
      base-url: http://<SERVER_HOST>:8080
    agent:
      hadoop-home: <HADOOP_HOME_PATH>
      work-dir: /opt/hdfs-transfer/agent/work
```

### 3. 构建

```bash
# 方式一：一键构建（推荐）
.\build.ps1        # Windows
./build.sh         # Linux

# 方式二：单独构建
# 后端（需先停掉运行中的 java 进程）
mvn package -pl server -am -DskipTests -q
# Agent
mvn package -pl agent -am -DskipTests -q
# 前端
cd web && npm install && npm run build

# 产物：
# deploy/server/server-1.0.0.jar + application.yml + start.sh + stop.sh
# deploy/agent/agent-1.0.0.jar + application.yml + start.sh + stop.sh
# deploy/web/dist/ + nginx.conf
```

### 4. 部署 Server

```bash
# Linux
cd deploy/server
./start.sh

# Windows（需用 UseShellExecute 启动，详见 AGENTS.md）

# 验证
curl http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```

### 5. 部署 Agent

```bash
# 上传部署包到 Linux 机器
scp -r deploy/agent/ user@<AGENT_HOST>:/opt/hdfs-transfer/agent/

# 修改配置
cd /opt/hdfs-transfer/agent
vim application.yml  # 修改 base-url 和 hadoop-home

# 转换脚本格式
dos2unix start.sh stop.sh

# 启动
./start.sh

# 查看日志
tail -f logs/agent-*.log

# 停止
./stop.sh
```

### 6. 部署前端

```bash
cd web
npm install

# 开发模式
npm run dev  # http://localhost:3000

# 生产构建
npm run build  # 产物在 web/dist/
```

### 7. 生产环境 Nginx 配置

构建脚本会自动生成 `deploy/web/nginx.conf`，内容参考：

```nginx
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://<SERVER_HOST>:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 默认账号

| 用户名 | 密码 | 说明 |
|--------|------|------|
| admin | admin123 | schema.sql 内置的默认管理员，请登录后及时修改密码 |

## 核心业务流程

### 任务执行流程

```
用户创建任务 → status=draft
  ↓
用户点击启动 → status=pending
  ↓
TaskDispatchJob(10s) → 分配给 Agent → status=dispatching
  ↓
TaskPollerService(15s) → Agent 拉取任务 → CAS → status=running
  ↓
PathExpressionResolver → 替换日期占位符 ${YYYY-MM-DD+N}
  ↓
PreCheckService → 检查 Hadoop 环境 + 源路径 + 目标空间
  ↓
getSourceStats → hadoop fs -count + du -s 获取总量
  ↓
ShellScriptGenerator → 生成 distcp bash 脚本
  ↓
ShellProcessManager → 执行 bash，解析 map X% reduce Y% 进度
  ↓
reportThrottled → 上报进度（每 20 行或 10s）
  ↓
distcp 完成（exitCode=0）→ status=success → completed=total
  ↓
DataVerifier → hadoop fs -count + du -s 比对源/目标 → 上报校验结果
  ↓
distcp 失败 → status=failed → RetryHandler → 加 -update 重试（最多 3 次，指数退避）
```

### 任务状态流转

```
draft → pending → dispatching → running → success
                          │              ↓
                          │          failed → retrying → running → ...
                          ↓
                       stopped（手动停止）/ killed（强制终止 + HDFS 清理）
```

| 状态 | 说明 | 前端按钮 |
|------|------|----------|
| draft | 草稿（创建后初始状态） | 编辑、启动、操作记录、删除 |
| pending | 待执行（用户点击启动后） | 停止、操作记录、删除 |
| dispatching | 派发中（Server 已分配 Agent） | 停止、操作记录 |
| running | 运行中（Agent 已拉取执行） | 强制终止、日志、操作记录 |
| retrying | 重试中（失败后自动重试） | 强制终止、日志、操作记录 |
| success | 已完成 | 日志、校验结果、操作记录 |
| failed | 失败 | 日志、操作记录 |
| stopped | 已停止 | 日志、操作记录、删除 |
| killed | 已终止（强制终止 + HDFS 清理） | 日志、操作记录 |

### 认证流程

```
用户登录 → POST /api/auth/login → 返回 JWT token
  ↓
前端存储 token 到 localStorage
  ↓
所有请求自动添加 Authorization: Bearer {token}
  ↓
JwtAuthenticationFilter 解析 token → 设置 SecurityContext
  ↓
Controller 方法执行 → 完成后返回 ApiResponse
```

## 定时任务

| 任务 | 模块 | 间隔 | 说明 |
|------|------|------|------|
| TaskDispatchJob | Server | 10s | 分配 pending/retrying 任务给 Agent |
| AgentMonitorJob | Server | 30s | 检查 Agent 心跳超时（60s），标记下线 |
| LogCleanupJob | Server | 每天 3:00 | 清理过期日志（默认 30 天） |
| HeartbeatService | Agent | 10s | 上报心跳 + CPU/内存 + 任务进度 |
| TaskPollerService | Agent | 15s | 拉取已分配任务 |
| LogCollector | Agent | 5s | 批量上报日志 |

## 开发注意事项

1. **JDK 1.8 兼容**：详见 `AGENTS.md` 中的 JDK 8 兼容性红线
2. **YAML 缩进**：Spring Boot 配置对缩进敏感，特别注意 `spring:` 和 `logging:` 层级
3. **Agent 部署**：每次重新构建 Agent 后，需重新写入 `deploy/` 目录的 start.sh/stop.sh/application.yml
4. **数据库变更**：新增字段时需手动执行 ALTER TABLE，schema.sql 仅用于初始建表
5. **Spring Boot 2.x**：使用 `javax.servlet.*` 而非 `jakarta.servlet.*`，使用 `antMatchers()` 而非 `requestMatchers()`
6. **schema.sql 不自动执行**：Spring Boot 默认仅对内嵌数据库自动执行 SQL 脚本，MySQL 需手动执行
