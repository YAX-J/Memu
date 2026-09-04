package com.memu.integration;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 ICS (RFC 5545) 解析器，只取日历集成需要的字段。
 *
 * 支持：折行展开、VEVENT 块切分、UID/DTSTART/DTEND/SUMMARY/DESCRIPTION、
 * 文本反转义、DTSTART 的 UTC(Z) 与浮动时间（浮动按系统时区解释）。
 * 不做：TZID 时区换算（示例数据统一用 UTC）、递归事件、VALARM 等。
 */
public final class IcsParser {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    public record IcsEvent(String uid, OffsetDateTime start, OffsetDateTime end,
                           String summary, String description) {
    }

    private IcsParser() {
    }

    public static List<IcsEvent> parse(String icsText) {
        String unfolded = unfold(icsText);
        List<IcsEvent> out = new ArrayList<>();
        int idx = 0;
        while (true) {
            int begin = unfolded.indexOf("BEGIN:VEVENT", idx);
            if (begin < 0) {
                break;
            }
            int end = unfolded.indexOf("END:VEVENT", begin);
            if (end < 0) {
                break;
            }
            String block = unfolded.substring(begin, end);
            out.add(parseBlock(block));
            idx = end + "END:VEVENT".length();
        }
        return out;
    }

    /** RFC 5545 折行：CRLF + 空格/制表符开头的行是上一行续行。 */
    static String unfold(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                // 续行：去掉前导空格拼到上一行末尾
                sb.append(line.substring(1));
            } else {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static IcsEvent parseBlock(String block) {
        Map<String, String> props = new LinkedHashMap<>();
        for (String line : block.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String rawName = line.substring(0, colon);
            String value = unescape(line.substring(colon + 1));
            // 名字可能带参数：DTSTART;TZID=Asia/Shanghai
            int semi = rawName.indexOf(';');
            String name = semi > 0 ? rawName.substring(0, semi) : rawName;
            props.putIfAbsent(name.toUpperCase(), value);
        }
        return new IcsEvent(
                props.getOrDefault("UID", ""),
                parseDt(props.get("DTSTART")),
                parseDt(props.get("DTEND")),
                props.getOrDefault("SUMMARY", ""),
                props.getOrDefault("DESCRIPTION", "")
        );
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\N", "\n")
                .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }

    private static OffsetDateTime parseDt(String value) {
        if (value == null || value.length() < 15) {
            return null;
        }
        String body = value.substring(0, 15); // yyyyMMdd'T'HHmmss
        try {
            LocalDateTime ldt = LocalDateTime.parse(body, DT);
            if (value.endsWith("Z")) {
                return ldt.atOffset(ZoneOffset.UTC);
            }
            return ldt.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        } catch (Exception e) {
            return null;
        }
    }
}
