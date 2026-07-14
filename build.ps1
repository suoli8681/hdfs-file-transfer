# Build and deploy script for HDFS File Transfer
# Usage: .\build.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "===== Building Server =====" -ForegroundColor Cyan
mvn package -pl server -am -DskipTests -q
if (!$?) { Write-Host "Server build failed" -ForegroundColor Red; exit 1 }

Write-Host "===== Building Agent =====" -ForegroundColor Cyan
mvn clean package -pl agent -am -DskipTests -q
if (!$?) { Write-Host "Agent build failed" -ForegroundColor Red; exit 1 }

Write-Host "===== Creating deploy directory =====" -ForegroundColor Cyan
$deployDir = Join-Path $root "deploy"
$serverDir = Join-Path $deployDir "server"
$agentDir = Join-Path $deployDir "agent"

# Clean and recreate
if (Test-Path $deployDir) { Remove-Item $deployDir -Recurse -Force }
New-Item -ItemType Directory -Path $serverDir -Force | Out-Null
New-Item -ItemType Directory -Path $agentDir -Force | Out-Null

# Copy server jar
Copy-Item "$root\server\target\server-1.0.0.jar" "$serverDir\server-1.0.0.jar"
Write-Host "Server jar copied to deploy\server\" -ForegroundColor Green

# Create server application.yml
$serverYml = @"
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
"@
Set-Content -Path "$serverDir\application.yml" -Value $serverYml -Encoding UTF8

# Create server start.sh
$serverStartSh = @"
#!/bin/bash
# Server startup script
cd "`$(dirname "`$0")"
nohup java -jar server-1.0.0.jar --spring.config.location=application.yml > /dev/null 2>&1 &
echo `$! > server.pid
echo "Server started, PID: `$(cat server.pid)"
"@
Set-Content -Path "$serverDir\start.sh" -Value $serverStartSh -Encoding ASCII

# Create server stop.sh
$serverStopSh = @"
#!/bin/bash
# Server stop script
cd "`$(dirname "`$0")"
if [ -f server.pid ]; then
    PID=`$(cat server.pid)
    if kill -0 "`$PID" 2>/dev/null; then
        kill "`$PID"
        echo "Server stopped, PID: `$PID"
    else
        echo "Process `$PID not running"
    fi
    rm -f server.pid
else
    echo "No PID file found"
fi
"@
Set-Content -Path "$serverDir\stop.sh" -Value $serverStopSh -Encoding ASCII

# Copy agent jar
Copy-Item "$root\agent\target\agent-1.0.0.jar" "$agentDir\agent-1.0.0.jar"
Write-Host "Agent jar copied to deploy\agent\" -ForegroundColor Green

# Create agent application.yml
$agentYml = @"
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
"@
Set-Content -Path "$agentDir\application.yml" -Value $agentYml -Encoding UTF8

# Create agent start.sh
$startSh = @"
#!/bin/bash
# Agent startup script
cd "`$(dirname "`$0")"
nohup java -jar agent-1.0.0.jar --spring.config.location=application.yml > /dev/null 2>&1 &
echo `$! > agent.pid
echo "Agent started, PID: `$(cat agent.pid)"
"@
Set-Content -Path "$agentDir\start.sh" -Value $startSh -Encoding ASCII

# Create agent stop.sh
$stopSh = @"
#!/bin/bash
# Agent stop script
cd "`$(dirname "`$0")"
if [ -f agent.pid ]; then
    PID=`$(cat agent.pid)
    if kill -0 "`$PID" 2>/dev/null; then
        kill "`$PID"
        echo "Agent stopped, PID: `$PID"
    else
        echo "Process `$PID not running"
    fi
    rm -f agent.pid
else
    echo "No PID file found"
fi
"@
Set-Content -Path "$agentDir\stop.sh" -Value $stopSh -Encoding ASCII

Write-Host ""
Write-Host "===== Deploy complete =====" -ForegroundColor Green
Write-Host "deploy\server\server-1.0.0.jar"
Write-Host "deploy\server\application.yml"
Write-Host "deploy\server\start.sh"
Write-Host "deploy\server\stop.sh"
Write-Host "deploy\agent\agent-1.0.0.jar"
Write-Host "deploy\agent\application.yml"

# Build frontend
Write-Host ""
Write-Host "===== Building Frontend =====" -ForegroundColor Cyan
Push-Location "$root\web"
if (-not (Test-Path "node_modules")) {
    Write-Host "Installing npm dependencies..." -ForegroundColor Yellow
    npm install
}
npm run build
Pop-Location
if (!$?) { Write-Host "Frontend build failed" -ForegroundColor Red; exit 1 }

# Copy frontend dist to deploy
$webDir = Join-Path $deployDir "web"
New-Item -ItemType Directory -Path $webDir -Force | Out-Null
Copy-Item "$root\web\dist\*" "$webDir\" -Recurse -Force
Write-Host "Frontend dist copied to deploy\web\" -ForegroundColor Green

# Create nginx.conf
$nginxConf = @"
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files `$uri `$uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://192.168.1.125:8080;
        proxy_set_header Host `$host;
        proxy_set_header X-Real-IP `$remote_addr;
        proxy_set_header X-Forwarded-For `$proxy_add_x_forwarded_for;
    }
}
"@
Set-Content -Path "$webDir\nginx.conf" -Value $nginxConf -Encoding UTF8

Write-Host ""
Write-Host "===== Deploy complete =====" -ForegroundColor Green
Write-Host "deploy\server\       (jar + yml + start.sh + stop.sh)"
Write-Host "deploy\agent\         (jar + yml + start.sh + stop.sh)"
Write-Host "deploy\web\           (dist + nginx.conf)"
