package com.hdfs.transfer.agent.verify;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.communication.ServerCommunicator;
import com.hdfs.transfer.agent.executor.ShellScriptGenerator;
import com.hdfs.transfer.agent.executor.ShellProcessManager;
import com.hdfs.transfer.agent.executor.SourceFileLister;
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
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "FILE_SIZE\\|(.+?)\\|(-?\\d+)");

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

    public void verify(Long taskId, String sourcePath, String targetPath, List<String> sourceFileList) {
        log.info("Starting verification for task {}", taskId);
        VerifyResultDTO result = new VerifyResultDTO();
        result.setTaskId(taskId);

        try {
            String script = scriptGenerator.generateVerifyScript(taskId, sourcePath, targetPath, sourceFileList);
            String workDir = agentConfig.getWorkDir() + File.separator + "verify_" + taskId;
            Process process = processManager.startScript(taskId, script, workDir);

            Map<String, Long> srcSizes = new HashMap<>();
            Map<String, Long> tgtSizes = new HashMap<>();
            boolean inDiffSection = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logCollector.collectLog(taskId, line);

                    Matcher sizeMatcher = SIZE_PATTERN.matcher(line);
                    if (sizeMatcher.find()) {
                        String path = sizeMatcher.group(1);
                        long size = Long.parseLong(sizeMatcher.group(2));
                        if (path.startsWith("SRC:")) {
                            srcSizes.put(path.substring(4), size);
                        } else if (path.startsWith("TGT:")) {
                            tgtSizes.put(path.substring(4), size);
                        }
                    }
                }
            }

            process.waitFor();
            processManager.removeProcess(taskId);

            long sourceFileCount = 0;
            long sourceTotalSize = 0;
            long targetFileCount = 0;
            long targetTotalSize = 0;
            List<String> diffFilePaths = new ArrayList<>();
            List<VerifyResultDTO.DiffFileInfo> diffList = new ArrayList<>();

            for (String sourceFilePath : sourceFileList) {
                long srcSize = srcSizes.getOrDefault(sourceFilePath, -1l);
                if (srcSize < 0) {
                    diffFilePaths.add(sourceFilePath);
                    VerifyResultDTO.DiffFileInfo info = new VerifyResultDTO.DiffFileInfo();
                    info.setFilePath(sourceFilePath);
                    info.setDiffType("missing_in_source");
                    diffList.add(info);
                    continue;
                }
                sourceFileCount++;
                sourceTotalSize += srcSize;

                String targetFilePath = SourceFileLister.getTargetFilePath(sourcePath, targetPath, sourceFilePath);
                long tgtSize = tgtSizes.getOrDefault(targetFilePath, -1l);
                if (tgtSize < 0) {
                    diffFilePaths.add(sourceFilePath);
                    VerifyResultDTO.DiffFileInfo info = new VerifyResultDTO.DiffFileInfo();
                    info.setFilePath(sourceFilePath);
                    info.setDiffType("missing_in_target");
                    diffList.add(info);
                    continue;
                }
                targetFileCount++;
                targetTotalSize += tgtSize;

                if (srcSize != tgtSize) {
                    diffFilePaths.add(sourceFilePath);
                    VerifyResultDTO.DiffFileInfo info = new VerifyResultDTO.DiffFileInfo();
                    info.setFilePath(sourceFilePath);
                    info.setDiffType("size_mismatch");
                    diffList.add(info);
                }
            }

            result.setSourceFileCount(sourceFileCount);
            result.setSourceTotalSize(sourceTotalSize);
            result.setTargetFileCount(targetFileCount);
            result.setTargetTotalSize(targetTotalSize);

            if (diffList.isEmpty()) {
                result.setVerifyStatus("match");
                log.info("Task {} verification MATCH", taskId);
            } else {
                result.setVerifyStatus("mismatch");
                result.setDiffFiles(diffFilePaths);
                result.setDiffDetails(diffList);
                log.info("Task {} verification MISMATCH: {} differences", taskId, diffList.size());
            }

        } catch (Exception e) {
            log.error("Verification failed for task {}", taskId, e);
            result.setVerifyStatus("error");
            result.setErrorMessage(e.getMessage());
        }

        result.setTimestamp(System.currentTimeMillis());
        communicator.uploadVerifyResult(result);
    }
}
