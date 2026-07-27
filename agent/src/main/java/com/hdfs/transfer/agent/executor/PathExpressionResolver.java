package com.hdfs.transfer.agent.executor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathExpressionResolver {

    private static final Pattern EXPR_PATTERN =
            Pattern.compile("\\$\\{([Yy]{4}[-]?[Mm]{2}[-]?[Dd]{2}(?:[ T]?[Hh]{2}(?::?[Mm]{2}(?::?[Ss]{2})?)?)?)([+-]\\d+)?\\}");

    public static String resolve(String path) {
        if (path == null || path.isEmpty()) return path;
        if (!path.contains("${")) return path;

        Matcher matcher = EXPR_PATTERN.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String formatPart = matcher.group(1);
            String offsetPart = matcher.group(2);

            LocalDateTime dateTime = LocalDateTime.now();
            if (offsetPart != null) {
                int offset = Integer.parseInt(offsetPart);
                if (formatPart.contains("ss") || formatPart.contains("SS")) {
                    dateTime = dateTime.plusSeconds(offset);
                } else if ((formatPart.contains("mm") && (formatPart.contains("HH") || formatPart.contains("hh")))
                        || (formatPart.contains("MM") && formatPart.contains(":"))) {
                    dateTime = dateTime.plusMinutes(offset);
                } else if (formatPart.contains("HH") || formatPart.contains("hh")) {
                    dateTime = dateTime.plusHours(offset);
                } else if (formatPart.contains("dd") || formatPart.contains("DD")) {
                    dateTime = dateTime.plusDays(offset);
                } else if (formatPart.contains("MM")) {
                    dateTime = dateTime.plusMonths(offset);
                } else {
                    dateTime = dateTime.plusYears(offset);
                }
            }

            String javaFormat = formatPart
                    .replace("YYYY", "yyyy")
                    .replace("DD", "dd")
                    .replace("HH", "HH");
            String resolved = dateTime.format(DateTimeFormatter.ofPattern(javaFormat));
            matcher.appendReplacement(sb, resolved);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
