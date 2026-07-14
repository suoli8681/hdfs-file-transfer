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

    public boolean preCheck(Long taskId, String sourcePath, String targetPath) {
        try {
            if (!checkHadoopEnv()) {
                log.error("Hadoop environment not available");
                return false;
            }

            if (!checkSourceExists(sourcePath)) {
                log.error("Source path does not exist: {}", sourcePath);
                return false;
            }

            if (!checkTargetSpace(sourcePath, targetPath)) {
                log.error("Target cluster has insufficient space");
                return false;
            }

            log.info("Pre-check passed for task {}", taskId);
            return true;
        } catch (Exception e) {
            log.error("Pre-check failed for task {}", taskId, e);
            return false;
        }
    }

    private boolean checkHadoopEnv() {
        try {
            String hadoopHome = agentConfig.getHadoopHome();
            File hadoopBin = new File(hadoopHome, "bin/hadoop");
            if (!hadoopBin.exists()) {
                log.warn("hadoop binary not found at {}", hadoopBin);
                return false;
            }
            ProcessBuilder pb = new ProcessBuilder(hadoopBin.getAbsolutePath(), "version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("Hadoop env check failed", e);
            return false;
        }
    }

    private boolean checkSourceExists(String sourcePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    agentConfig.getHadoopHome() + "/bin/hadoop", "fs", "-test", "-e", sourcePath);
            pb.environment().put("HADOOP_HOME", agentConfig.getHadoopHome());
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            log.warn("Source path check failed for {}", sourcePath, e);
            return false;
        }
    }

    private boolean checkTargetSpace(String sourcePath, String targetPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    agentConfig.getHadoopHome() + "/bin/hadoop", "fs", "-df", targetPath);
            pb.environment().put("HADOOP_HOME", agentConfig.getHadoopHome());
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            log.info("Target cluster space: {}", output.toString().trim());
            return true;
        } catch (Exception e) {
            log.warn("Target space check failed, bypass", e);
            return true;
        }
    }
}