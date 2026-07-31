package com.hdfs.transfer.agent.executor;

import com.hdfs.transfer.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class SourceFileLister {

    private static final Logger log = LoggerFactory.getLogger(SourceFileLister.class);

    private final AgentConfig agentConfig;

    public SourceFileLister(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    public List<String> listFiles(String sourcePath) {
        List<String> files = new ArrayList<>();
        try {
            String hadoopBin = agentConfig.getHadoopHome() + "/bin/hadoop";

            ProcessBuilder pb = new ProcessBuilder(hadoopBin, "fs", "-ls", "-R", sourcePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Found")) continue;
                    String filePath = extractFilePath(line);
                    if (filePath != null && !filePath.isEmpty()) {
                        files.add(filePath);
                    }
                }
            }
            process.waitFor();
            log.info("Listed {} files from source path: {}", files.size(), sourcePath);
        } catch (Exception e) {
            log.error("Failed to list files for source path: {}", sourcePath, e);
        }
        return files;
    }

    private String extractFilePath(String lsLine) {
        String[] parts = lsLine.trim().split("\\s+");
        if (parts.length < 8) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 7; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public static String getTargetFilePath(String sourcePath, String targetPath, String sourceFilePath) {
        String src = normalizePath(sourcePath);
        String tgt = normalizeTargetPath(targetPath);

        boolean hasWildcard = src.contains("*") || src.contains("?");
        boolean trailingSlash = sourcePath.endsWith("/");

        if (hasWildcard) {
            int wildcardIdx = src.lastIndexOf('/');
            String wildcardParent = wildcardIdx > 0 ? src.substring(0, wildcardIdx) : "";
            if (sourceFilePath.startsWith(wildcardParent)) {
                String relative = sourceFilePath.substring(wildcardParent.length());
                if (!relative.startsWith("/")) relative = "/" + relative;
                return tgt + relative;
            }
            return tgt + "/" + sourceFilePath;
        }

        if (trailingSlash) {
            if (sourceFilePath.startsWith(src)) {
                String relative = sourceFilePath.substring(src.length());
                if (!relative.startsWith("/")) relative = "/" + relative;
                return tgt + relative;
            }
            return tgt + "/" + sourceFilePath;
        }

        String srcBasename = getBasename(src);
        String srcParent = src.substring(0, src.lastIndexOf('/'));
        if (sourceFilePath.startsWith(srcParent)) {
            String relative = sourceFilePath.substring(srcParent.length());
            if (!relative.startsWith("/")) relative = "/" + relative;
            return tgt + relative;
        }
        return tgt + "/" + sourceFilePath;
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    public static String normalizeTargetPath(String path) {
        if (path == null) return "";
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String getBasename(String path) {
        path = normalizePath(path);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
}
