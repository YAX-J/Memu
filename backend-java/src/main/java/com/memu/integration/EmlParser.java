package com.memu.integration;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 .eml (RFC 5322) 解析器，只取邮件集成需要的字段。
 *
 * 支持：头部/正文切分、头部折行展开、From/To/Subject/Date/Message-ID、
 * Subject 的 `=?UTF-8?B?...?=` base64 编码解码、正文 base64 解码。
 * 不做：完整 MIME 多段（multipart）、quoted-printable、内嵌附件。
 */
public final class EmlParser {

    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH);

    public record EmlMessage(String from, String to, String subject,
                             OffsetDateTime date, String body, String messageId) {
    }

    private EmlParser() {
    }

    public static EmlMessage parse(String emlText) {
        String normalized = emlText.replace("\r\n", "\n").replace("\r", "\n");
        int sep = normalized.indexOf("\n\n");
        String headerPart = sep >= 0 ? normalized.substring(0, sep) : normalized;
        String bodyPart = sep >= 0 ? normalized.substring(sep + 2) : "";

        Map<String, String> headers = parseHeaders(headerPart);
        String subject = decodeHeader(headers.getOrDefault("Subject", ""));
        String from = decodeHeader(headers.getOrDefault("From", ""));
        String to = decodeHeader(headers.getOrDefault("To", ""));
        String messageId = headers.getOrDefault("Message-ID", "").trim();
        OffsetDateTime date = parseDate(headers.get("Date"));
        String body = decodeBody(bodyPart, headers.getOrDefault("Content-Transfer-Encoding", ""));

        return new EmlMessage(from, to, subject, date, body, messageId);
    }

    /** 头部行解析，处理折行（续行以空格/制表符开头）。 */
    static Map<String, String> parseHeaders(String headerPart) {
        Map<String, String> headers = new LinkedHashMap<>();
        String prevName = null;
        for (String line : headerPart.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                // 续行
                if (prevName != null) {
                    headers.merge(prevName, line.substring(1), (a, b) -> a + b);
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            prevName = name;
            headers.put(name, value);
        }
        return headers;
    }

    /** 解码 MIME 编码头：`=?UTF-8?B?...?=`（base64）。 */
    static String decodeHeader(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int idx = 0;
        while (idx < value.length()) {
            int start = value.indexOf("=?", idx);
            if (start < 0) {
                out.append(value.substring(idx));
                break;
            }
            out.append(value, idx, start);
            int end = value.indexOf("?=", start + 2);
            if (end < 0) {
                out.append(value.substring(start));
                break;
            }
            String chunk = value.substring(start + 2, end);
            // 形如 "UTF-8?B?<base64>"，拆成 charset / encoding / data 三段
            String[] parts = chunk.split("\\?", 3);
            if (parts.length == 3 && "B".equalsIgnoreCase(parts[1])) {
                try {
                    out.append(new String(Base64.getDecoder().decode(parts[2]),
                            java.nio.charset.StandardCharsets.UTF_8));
                } catch (IllegalArgumentException e) {
                    out.append(parts[2]);
                }
            } else {
                out.append(parts.length == 3 ? parts[2] : chunk);
            }
            idx = end + 2;
        }
        return out.toString();
    }

    private static OffsetDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value.trim(), RFC1123).toOffsetDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeBody(String body, String encoding) {
        String trimmed = body.trim();
        if ("base64".equalsIgnoreCase(encoding.trim())) {
            try {
                String compact = trimmed.replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(compact),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return trimmed;
            }
        }
        return trimmed;
    }
}
