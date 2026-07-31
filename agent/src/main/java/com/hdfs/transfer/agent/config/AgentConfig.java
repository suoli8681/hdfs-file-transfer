package com.hdfs.transfer.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentConfig {

    @Value("${hdfs.transfer.server.base-url}")
    private String serverBaseUrl;

    @Value("${hdfs.transfer.server.heartbeat-interval:10}")
    private int heartbeatInterval;

    @Value("${hdfs.transfer.agent.max-parallel-tasks:3}")
    private int maxParallelTasks;

    @Value("${hdfs.transfer.agent.log-batch-size:50}")
    private int logBatchSize;

    @Value("${hdfs.transfer.agent.log-collect-interval:5}")
    private int logCollectInterval;

    @Value("${hdfs.transfer.agent.retry-enabled:true}")
    private boolean retryEnabled;

    @Value("${hdfs.transfer.agent.retry-max-count:3}")
    private int retryMaxCount;

    @Value("${hdfs.transfer.agent.work-dir:/opt/hdfs-transfer/agent/work}")
    private String workDir;

    @Value("${hdfs.transfer.agent.hadoop-home:/opt/cloudera/parcels/CDH/lib/hadoop}")
    private String hadoopHome;

    @Value("${hdfs.transfer.agent.yarn-bin:/opt/cloudera/parcels/CDH/lib/hadoop-yarn}")
    private String yarnBin;

    @Value("${server.port:8081}")
    private int agentPort;

    @Value("${hdfs.transfer.agent.task-timeout-hours:0}")
    private int taskTimeoutHours;

    private String agentId;
    private String agentHost;

    public String getServerBaseUrl() { return serverBaseUrl; }
    public int getHeartbeatInterval() { return heartbeatInterval; }
    public int getMaxParallelTasks() { return maxParallelTasks; }
    public int getLogBatchSize() { return logBatchSize; }
    public int getLogCollectInterval() { return logCollectInterval; }
    public boolean isRetryEnabled() { return retryEnabled; }
    public int getRetryMaxCount() { return retryMaxCount; }
    public String getWorkDir() { return workDir; }
    public String getHadoopHome() { return hadoopHome; }
    public String getYarnBin() {
        if (yarnBin == null || yarnBin.isEmpty()) {
            return hadoopHome + "/bin/yarn";
        }
        return yarnBin + "/bin/yarn";
    }
    public int getAgentPort() { return agentPort; }
    public int getTaskTimeoutHours() { return taskTimeoutHours; }

    public String getAgentId() {
        if (agentId == null) {
            agentId = "agent-" + getAgentHost() + "-" + agentPort;
        }
        return agentId;
    }

    public String getAgentHost() {
        if (agentHost == null) {
            try {
                agentHost = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                agentHost = "127.0.0.1";
            }
        }
        return agentHost;
    }
}