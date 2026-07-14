package com.hdfs.transfer.agent.verify;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.executor.ShellScriptGenerator;
import com.hdfs.transfer.agent.executor.ShellProcessManager;
import com.hdfs.transfer.agent.monitor.LogCollector;
import com.hdfs.transfer.common.dto.VerifyResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataVerifier {

    private static final Logger log = LoggerFactory.getLogger(DataVerifier.class);
    private static final Pattern VERIFY_PATTERN = Pattern.compile(
            "VERIFY_RESULT: SRC_COUNT=(\\d+) SRC_SIZE=(\\d+) TGT_COUNT=(\\d+) TGT_SIZE=(\\d+)");
    private static final Pattern DIFF_PATTERN = Pattern.compile(
            "^(\\d+|[<>])(.*)$");

    private final AgentConfig agentConfig;
    private final ShellScriptGenerator scriptGenerator;
    private final ShellProcessManager processManager;
    private final LogCollector logCollector;
    private final ServerCommunicator communicator;

    public DataVerifier(AgentConfig agentConfig, ShellScriptGenerator scriptGenerator,
                        ShellProcessManager processManager, LogCollector logCollector,
                        ServerCommunicator communicator) {
        this.agentConfig = agentConfig;
        this.scriptGenerator = scriptGenerator;
        this.processManager = processManager;
        this.logCollector = logCollector;
        this.communicator = communicator;
    }

    public void verify(Long taskId, String sourcePath, String targetPath) {
        log.info("Starting verification for task {}", taskId);
        try {
            String script = scriptGenerator.generateVerifyScript(taskId, sourcePath, targetPath);
            String workDir = agentConfig.getWorkDir() + File.separator + "verify_" + taskId;
            Process process = processManager.startScript(taskId, script, workDir);

            VerifyResultDTO result = new VerifyResultDTO();
            result.setTaskId(taskId);
            List<VerifyResultDTO.DiffFileInfo> diffList = new ArrayList<>();
            List<String> diffFilePaths = new ArrayList<>();
            boolean inDiffSection = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logCollector.collectLog(taskId, line);

                    Matcher matcher = VERIFY_PATTERN.matcher(line);
                    if (matcher.find()) {
                        result.setSourceFileCount(Long.parseLong(matcher.group(1)));
                        result.setSourceTotalSize(Long.parseLong(matcher.group(2)));
                        result.setTargetFileCount(Long.parseLong(matcher.group(3)));
                        result.setTargetTotalSize(Long.parseLong(matcher.group(4)));
                    }

                    if (line.contains("--- DIFF_LIST ---")) {
                        inDiffSection = true;
                        continue;
                    }
                    if (line.contains("--- END_DIFF_LIST ---")) {
                        inDiffSection = false;
                        continue;
                    }
                    if (inDiffSection && !line.trim().isEmpty()) {
                        diffFilePaths.add(line.trim());
                    }
                }
            }

            int exitCode = process.waitFor();
            processManager.removeProcess(taskId);

            if (exitCode == 0) {
                result.setVerifyStatus("match");
            } else {
                result.setVerifyStatus("mismatch");
                result.setDiffFiles(diffFilePaths);
                for (String diffPath : diffFilePaths) {
                    VerifyResultDTO.DiffFileInfo info = new VerifyResultDTO.DiffFileInfo();
                    info.setFilePath(diffPath);
                    info.setDiffType(diffPath.startsWith("<") ? "missing_in_target" : "extra_in_target");
                    diffList.add(info);
                }
                result.setDiffDetails(diffList);
            }

            result.setTimestamp(System.currentTimeMillis());
            communicator.uploadVerifyResult(result);
            log.info("Verification completed for task {}: {}", taskId, result.getVerifyStatus());

        } catch (Exception e) {
            log.error("Verification failed for task {}", taskId, e);
            VerifyResultDTO result = new VerifyResultDTO();
            result.setTaskId(taskId);
            result.setVerifyStatus("error");
            result.setErrorMessage(e.getMessage());
            result.setTimestamp(System.currentTimeMillis());
            communicator.uploadVerifyResult(result);
        }
    }
}