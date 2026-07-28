package com.hdfs.transfer.agent.precheck;

import com.hdfs.transfer.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class PreCheckService {

    private static final Logger log = LoggerFactory.getLogger(PreCheckService.class);

    private final AgentConfig agentConfig;

    public PreCheckService(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    public PreCheckResult preCheck(Long taskId, String sourcePath, String targetPath) {
        try {
            String hadoopBin = agentConfig.getHadoopHome() + "/bin/hadoop";
            File hadoopBinary = new File(hadoopBin);
            if (!hadoopBinary.exists()) {
                String msg = "Hadoop环境检查失败: 未找到Hadoop可执行文件 " + hadoopBin;
                log.error(msg);
                return PreCheckResult.fail(msg);
            }

            ProcessBuilder versionPb = new ProcessBuilder(hadoopBin, "version");
            Process versionProcess = versionPb.start();
            int versionExitCode = versionProcess.waitFor();
            if (versionExitCode != 0) {
                String msg = "Hadoop环境检查失败: hadoop version 命令执行失败(退出码=" + versionExitCode + ")";
                log.error(msg);
                return PreCheckResult.fail(msg);
            }

            int sourceExitCode = checkPathExists(sourcePath);
            if (sourceExitCode != 0) {
                String msg = "源路径不存在或无访问权限: " + sourcePath;
                log.error(msg);
                return PreCheckResult.fail(msg);
            }

            int targetParentExists = checkTargetParentExists(targetPath);
            if (targetParentExists != 0) {
                String msg = "目标路径的父目录不存在或无访问权限: " + targetPath;
                log.error(msg);
                return PreCheckResult.fail(msg);
            }

            int targetExists = checkPathExists(targetPath);
            if (targetExists != 0) {
                String msg = "目标路径不存在: " + targetPath;
                log.error(msg);
                return PreCheckResult.fail(msg);
            }

            log.info("Pre-check passed for task {}", taskId);
            return PreCheckResult.success();
        } catch (Exception e) {
            String msg = "预检查异常: " + e.getMessage();
            log.error("Pre-check failed for task {}", taskId, e);
            return PreCheckResult.fail(msg);
        }
    }

    private int checkPathExists(String path) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                agentConfig.getHadoopHome() + "/bin/hadoop", "fs", "-test", "-e", path);
        pb.environment().put("HADOOP_HOME", agentConfig.getHadoopHome());
        Process process = pb.start();
        return process.waitFor();
    }

    private int checkTargetParentExists(String targetPath) throws Exception {
        String parentPath = targetPath;
        if (targetPath.endsWith("/")) {
            parentPath = targetPath.substring(0, targetPath.length() - 1);
        }
        int lastSlash = parentPath.lastIndexOf('/');
        if (lastSlash > 0) {
            parentPath = parentPath.substring(0, lastSlash);
        } else {
            return 0;
        }
        return checkPathExists(parentPath);
    }

    public static class PreCheckResult {
        private final boolean success;
        private final String message;

        private PreCheckResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static PreCheckResult success() {
            return new PreCheckResult(true, "预检查通过");
        }

        public static PreCheckResult fail(String message) {
            return new PreCheckResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
