package com.hdfs.transfer.agent.executor;

import com.hdfs.transfer.agent.config.AgentConfig;
import org.springframework.stereotype.Component;

import java.io.File;

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

    public String generateVerifyScript(Long taskId, String sourcePath, String targetPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("set -o pipefail\n\n");

        sb.append("export HADOOP_HOME=").append(agentConfig.getHadoopHome()).append("\n");
        sb.append("export PATH=$HADOOP_HOME/bin:$PATH\n\n");

        sb.append("TASK_ID=").append(taskId).append("\n");
        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Verifying task $TASK_ID\"\n\n");

        sb.append("SRC_PATH=\"").append(sourcePath).append("\"\n");
        sb.append("TGT_PATH=\"").append(targetPath).append("\"\n\n");

        // Determine the actual target path for comparison based on distcp behavior
        // If source is a directory (not ending with /), distcp creates targetPath/basename(sourcePath)
        sb.append("SRC_BASENAME=$(basename \"$SRC_PATH\")\n");
        sb.append("if [[ \"$SRC_PATH\" != */ ]]; then\n");
        sb.append("    ACTUAL_TGT_PATH=\"$TGT_PATH/$SRC_BASENAME\"\n");
        sb.append("else\n");
        sb.append("    ACTUAL_TGT_PATH=\"$TGT_PATH\"\n");
        sb.append("fi\n\n");

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Source: $SRC_PATH\"\n");
        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Target (actual): $ACTUAL_TGT_PATH\"\n\n");

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Counting source files...\"\n");
        sb.append("SRC_COUNT=$(hadoop fs -count \"$SRC_PATH\" 2>/dev/null | awk '{print $2}')\n");
        sb.append("SRC_SIZE=$(hadoop fs -du -s \"$SRC_PATH\" 2>/dev/null | awk '{print $1}')\n\n");

        sb.append("echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Counting target files...\"\n");
        sb.append("TGT_COUNT=$(hadoop fs -count \"$ACTUAL_TGT_PATH\" 2>/dev/null | awk '{print $2}')\n");
        sb.append("TGT_SIZE=$(hadoop fs -du -s \"$ACTUAL_TGT_PATH\" 2>/dev/null | awk '{print $1}')\n\n");

        sb.append("echo \"VERIFY_RESULT: SRC_COUNT=$SRC_COUNT SRC_SIZE=$SRC_SIZE TGT_COUNT=$TGT_COUNT TGT_SIZE=$TGT_SIZE\"\n\n");

        sb.append("if [ \"$SRC_COUNT\" = \"$TGT_COUNT\" ] && [ \"$SRC_SIZE\" = \"$TGT_SIZE\" ]; then\n");
        sb.append("    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Verification MATCH\"\n");
        sb.append("    exit 0\n");
        sb.append("else\n");
        sb.append("    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] Verification MISMATCH: source=$SRC_COUNT files/$SRC_SIZE bytes, target=$TGT_COUNT files/$TGT_SIZE bytes\"\n");

        sb.append("    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] Listing differences...\"\n");
        sb.append("    hadoop fs -ls \"$SRC_PATH\" 2>/dev/null | awk '{print $8}' > /tmp/src_$TASK_ID.txt\n");
        sb.append("    hadoop fs -ls \"$ACTUAL_TGT_PATH\" 2>/dev/null | awk '{print $8}' > /tmp/tgt_$TASK_ID.txt\n");
        sb.append("    diff /tmp/src_$TASK_ID.txt /tmp/tgt_$TASK_ID.txt > /tmp/diff_$TASK_ID.txt 2>&1\n");
        sb.append("    echo \"--- DIFF_LIST ---\"\n");
        sb.append("    cat /tmp/diff_$TASK_ID.txt\n");
        sb.append("    echo \"--- END_DIFF_LIST ---\"\n");
        sb.append("    rm -f /tmp/src_$TASK_ID.txt /tmp/tgt_$TASK_ID.txt /tmp/diff_$TASK_ID.txt\n");
        sb.append("    exit 1\n");
        sb.append("fi\n");

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