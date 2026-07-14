#!/bin/bash
# Build and deploy script for HDFS File Transfer (Linux)
# Usage: ./build.sh

set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

echo "===== Building Server ====="
mvn package -pl server -am -DskipTests -q

echo "===== Building Agent ====="
mvn clean package -pl agent -am -DskipTests -q

echo "===== Creating deploy directory ====="
DEPLOY_DIR="$ROOT_DIR/deploy"
SERVER_DIR="$DEPLOY_DIR/server"
AGENT_DIR="$DEPLOY_DIR/agent"

rm -rf "$DEPLOY_DIR"
mkdir -p "$SERVER_DIR" "$AGENT_DIR"

# Copy jars
cp "$ROOT_DIR/server/target/server-1.0.0.jar" "$SERVER_DIR/"
cp "$ROOT_DIR/agent/target/agent-1.0.0.jar" "$AGENT_DIR/"

# Server application.yml
cat > "$SERVER_DIR/application.yml" << 'EOF'
server:
  port: 8080

spring:
  application:
    name: hdfs-file-transfer-server
  mvc:
    pathmatch:
      matching-strategy: ant_path_matcher
  datasource:
    url: jdbc:mysql://192.168.1.125:3306/hdfs_transfer?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: richinfo
    driver-class-name: com.mysql.cj.jdbc.Driver
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.hdfs.transfer.server.entity
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    map-underscore-to-camel-case: true

hdfs:
  transfer:
    agent-heartbeat-timeout: 60
    max-concurrent-tasks: 10
    default-retry-count: 3
    task-log-retention-days: 30
    dispatch-timeout-seconds: 120

alert:
  dingtalk:
    enabled: false
    webhook: ""
  wechat:
    enabled: false
    webhook: ""
  mail:
    enabled: false
    host: smtp.example.com
    port: 465
    username: ""
    password: ""
EOF

# Server start.sh
cat > "$SERVER_DIR/start.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
nohup java -jar server-1.0.0.jar --spring.config.location=application.yml > /dev/null 2>&1 &
echo $! > server.pid
echo "Server started, PID: $(cat server.pid)"
EOF

# Server stop.sh
cat > "$SERVER_DIR/stop.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
if [ -f server.pid ]; then
    PID=$(cat server.pid)
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "Server stopped, PID: $PID"
    else
        echo "Process $PID not running"
    fi
    rm -f server.pid
else
    echo "No PID file found"
fi
EOF

# Agent application.yml
cat > "$AGENT_DIR/application.yml" << 'EOF'
server:
  port: 8081

spring:
  application:
    name: hdfs-file-transfer-agent
  main:
    allow-circular-references: true

hdfs:
  transfer:
    server:
      base-url: http://192.168.1.125:8080
      heartbeat-interval: 10
    agent:
      max-parallel-tasks: 3
      log-batch-size: 50
      log-collect-interval: 5
      retry-enabled: true
      retry-max-count: 3
      work-dir: /opt/hdfs-transfer/agent/work
      hadoop-home: /opt/hadoop
      # Task timeout in hours, 0 = no timeout
      task-timeout-hours: 0
EOF

# Agent start.sh
cat > "$AGENT_DIR/start.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
nohup java -jar agent-1.0.0.jar --spring.config.location=application.yml > /dev/null 2>&1 &
echo $! > agent.pid
echo "Agent started, PID: $(cat agent.pid)"
EOF

# Agent stop.sh
cat > "$AGENT_DIR/stop.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "$0")"
if [ -f agent.pid ]; then
    PID=$(cat agent.pid)
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "Agent stopped, PID: $PID"
    else
        echo "Process $PID not running"
    fi
    rm -f agent.pid
else
    echo "No PID file found"
fi
EOF

chmod +x "$SERVER_DIR/start.sh" "$SERVER_DIR/stop.sh" "$AGENT_DIR/start.sh" "$AGENT_DIR/stop.sh"

# Build frontend
echo ""
echo "===== Building Frontend ====="
cd "$ROOT_DIR/web"
if [ ! -d "node_modules" ]; then
    echo "Installing npm dependencies..."
    npm install
fi
npm run build
cd "$ROOT_DIR"

# Copy frontend dist to deploy
WEB_DIR="$DEPLOY_DIR/web"
mkdir -p "$WEB_DIR"
cp -r "$ROOT_DIR/web/dist/"* "$WEB_DIR/"

# Create nginx.conf
cat > "$WEB_DIR/nginx.conf" << 'EOF'
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://192.168.1.125:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
EOF

echo ""
echo "===== Deploy complete ====="
echo "deploy/server/       (jar + yml + start.sh + stop.sh)"
echo "deploy/agent/         (jar + yml + start.sh + stop.sh)"
echo "deploy/web/           (dist + nginx.conf)"
