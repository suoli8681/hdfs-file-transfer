package com.hdfs.transfer.agent.executor;

import com.hdfs.transfer.agent.config.AgentConfig;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class ShellScriptGenerator {

    private final AgentConfig agentConfig;

    public ShellScriptGenerator(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    public String generateDistcpScript(Long taskId, String sourcePath, String targetPath,
                                        String sourceCluster, String targetCluster,
                                        String distcpOptions, String workDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("set -o pipefail\n\n");

        sb.append("export HADOOP_HOME=").append(agentConfig.getHadoopHome()).append("\n");
        sb.append("export PATH=$HADOOP_HOME/bin:$PATH\n\n");

        sb.append("TASK_ID=").append(taskId).append("\n");
        sb.append("WORK_DIR=\"").append(workDir).append("\"\n");
        sb.append("mkdir -p $WORK_DIR\n\n");

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Task $TASK_ID started\"\n");

        String src = sourcePath;
        String tgt = targetPath;

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Source: ").append(src).append("\"\n");
        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Target: ").append(tgt).append("\"\n");
        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Options: ").append(distcpOptions).append("\"\n\n");

        String options = (distcpOptions != null && !distcpOptions.isEmpty()) ? distcpOptions : "-D mapreduce.job.name=hdfs-transfer-task-$TASK_ID";
        sb.append("DISTCP_OPTS=\"").append(options).append("\"\n\n");

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Executing distcp...\"\n");
        sb.append("hadoop distcp $DISTCP_OPTS \"").append(src).append("\" \"").append(tgt).append("\" 2>&1\n");
        sb.append("EXIT_CODE=$?\n\n");

        sb.append("if [ $EXIT_CODE -eq 0 ]; then\n");
        sb.append("    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Task $TASK_ID completed successfully\"\n");
        sb.append("else\n");
        sb.append("    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] Task $TASK_ID failed with exit code $EXIT_CODE\"\n");
        sb.append("fi\n\n");
        sb.append("exit $EXIT_CODE\n");

        return sb.toString();
    }

    public String generateVerifyScript(Long taskId, String sourcePath, String targetPath, List<String> sourceFileList) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("set -o pipefail\n\n");

        sb.append("export HADOOP_HOME=").append(agentConfig.getHadoopHome()).append("\n");
        sb.append("export PATH=$HADOOP_HOME/bin:$PATH\n\n");

        sb.append("TASK_ID=").append(taskId).append("\n");
        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Verifying task $TASK_ID\"\n\n");

        sb.append("SRC_PATH=\"").append(sourcePath.replace("\"", "\\\"")).append("\"\n");
        sb.append("TGT_PATH=\"").append(targetPath.replace("\"", "\\\"")).append("\"\n\n");

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Getting source file sizes...\"\n");
        for (String filePath : sourceFileList) {
            sb.append("SRC_SIZE=$(hadoop fs -du \"").append(filePath.replace("\"", "\\\"")).append("\" 2>/dev/null | awk '{print $1}')\n");
            sb.append("echo \"FILE_SIZE|SRC:").append(filePath).append("|${SRC_SIZE:--1}\"\n");
        }

        sb.append("\necho \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Getting target file sizes...\"\n");
        for (String sourceFilePath : sourceFileList) {
            String targetFilePath = SourceFileLister.getTargetFilePath(sourcePath, targetPath, sourceFilePath);
            sb.append("TGT_SIZE=$(hadoop fs -du \"").append(targetFilePath.replace("\"", "\\\"")).append("\" 2>/dev/null | awk '{print $1}')\n");
            sb.append("echo \"FILE_SIZE|TGT:").append(targetFilePath).append("|${TGT_SIZE:--1}\"\n");
        }

        sb.append("\necho \"--- DIFF_LIST ---\"\n");
        sb.append("echo \"--- END_DIFF_LIST ---\"\n");
        sb.append("exit 0\n");

        return sb.toString();
    }

    public String generateSourceStatScript(String sourcePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("export HADOOP_HOME=").append(agentConfig.getHadoopHome()).append("\n");
        sb.append("export PATH=$HADOOP_HOME/bin:$PATH\n\n");
        sb.append("hadoop fs -count \"").append(sourcePath).append("\" 2>/dev/null | awk '{print $2,$3}'\n");
        sb.append("hadoop fs -du -s \"").append(sourcePath).append("\" 2>/dev/null | awk '{print $1,$2}'\n");
        return sb.toString();
    }
}