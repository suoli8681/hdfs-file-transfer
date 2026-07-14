package com.hdfs.transfer.agent.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShellProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ShellProcessManager.class);

    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    public Process startScript(Long taskId, String scriptContent, String workDir) throws IOException {
        File workDirFile = new File(workDir);
        if (!workDirFile.exists()) {
            workDirFile.mkdirs();
        }

        File scriptFile = new File(workDir, "task_" + taskId + ".sh");
        try (FileWriter fw = new FileWriter(scriptFile)) {
            fw.write(scriptContent);
            fw.flush();
        }
        scriptFile.setExecutable(true);

        ProcessBuilder pb = new ProcessBuilder("/bin/bash", scriptFile.getAbsolutePath());
        pb.directory(workDirFile);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        runningProcesses.put(taskId, process);
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        log.info("Started process for task {} PID={}", taskId, pid);
        return process;
    }

    public void killProcess(Long taskId) {
        Process process = runningProcesses.get(taskId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            log.info("Killed process for task {}", taskId);
        }
        runningProcesses.remove(taskId);
    }

    public Process getProcess(Long taskId) {
        return runningProcesses.get(taskId);
    }

    public boolean isProcessAlive(Long taskId) {
        Process process = runningProcesses.get(taskId);
        return process != null && process.isAlive();
    }

    public void removeProcess(Long taskId) {
        runningProcesses.remove(taskId);
    }
}