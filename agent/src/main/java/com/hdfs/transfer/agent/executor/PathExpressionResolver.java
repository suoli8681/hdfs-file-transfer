package com.hdfs.transfer.agent.executor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathExpressionResolver {

    private static final Pattern EXPR_PATTERN =
            Pattern.compile("\\$\\{(YYYY(?:[-]?MM(?:[-]?DD)?)?)([+-]\\d+)?\\}");

    public static String resolve(String path) {
        if (path == null || path.isEmpty()) return path;
        if (!path.contains("${")) return path;

        Matcher matcher = EXPR_PATTERN.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String formatPart = matcher.group(1);
            String offsetPart = matcher.group(2);

            LocalDate date = LocalDate.now();
            if (offsetPart != null) {
                int offset = Integer.parseInt(offsetPart);
                date = date.plusDays(offset);
            }

            String javaFormat = formatPart.replace("YYYY", "yyyy").replace("DD", "dd");
            String resolved = date.format(DateTimeFormatter.ofPattern(javaFormat));
            matcher.appendReplacement(sb, resolved);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
