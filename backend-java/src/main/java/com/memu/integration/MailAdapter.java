package com.memu.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 邮件适配器（本地 .eml 目录版）。
 *
 * 读指定目录下的 .eml 文件（任何邮件客户端都能「导出 .eml」），抽取联系人、
 * 会议邀约等进入图谱。IMAP/系统邮件客户端留后续，先跑通本地文件这条链路。
 *
 * 增量：游标记录已处理的 Message-ID（无则用文件名），下次只取新的。
 */
@Component
public class MailAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(MailAdapter.class);

    private final Path emlDir;
    private final ObjectMapper mapper;

    public MailAdapter(
            @Value("${memu.mail.eml-dir:}") String emlDir,
            ObjectMapper mapper) {
        this.emlDir = (emlDir == null || emlDir.isBlank()) ? null : Path.of(emlDir);
        this.mapper = mapper;
    }

    @Override
    public String source() {
        return "mail";
    }

    @Override
    public SyncResult fetchIncremental(String fromCursor) {
        if (emlDir == null || !Files.isDirectory(emlDir)) {
            return new SyncResult(List.of(), fromCursor);
        }

        Set<String> seen = decodeCursor(fromCursor);
        Set<String> newSeen = new TreeSet<>(seen);
        List<EventItem> items = new ArrayList<>();

        try (Stream<Path> files = Files.list(emlDir)) {
            for (Path file : files.filter(p -> p.toString().toLowerCase().endsWith(".eml")).toList()) {
                String emlText;
                try {
                    emlText = Files.readString(file);
                } catch (IOException e) {
                    log.warn("读邮件文件失败 {}: {}", file, e.getMessage());
                    continue;
                }

                EmlParser.EmlMessage msg = EmlParser.parse(emlText);
                String dedupKey = !msg.messageId().isBlank() ? msg.messageId() : file.getFileName().toString();
                if (newSeen.contains(dedupKey)) {
                    continue;
                }
                newSeen.add(dedupKey);

                String rawText = buildRawText(msg);
                if (rawText.isBlank()) {
                    continue;
                }
                Map<String, Object> meta = Map.of(
                        "from", msg.from(),
                        "to", msg.to(),
                        "messageId", msg.messageId(),
                        "eventId", "mail:" + dedupKey
                );
                items.add(new EventItem("email", rawText, msg.date(), meta));
            }
        } catch (IOException e) {
            log.warn("扫描邮件目录失败 {}: {}", emlDir, e.getMessage());
        }

        String nextCursor = encodeCursor(newSeen);
        if (!items.isEmpty()) {
            log.info("邮件同步：新增 {} 封邮件", items.size());
        }
        return new SyncResult(items, nextCursor);
    }

    private String buildRawText(EmlParser.EmlMessage msg) {
        String subject = msg.subject() == null ? "" : msg.subject().trim();
        String body = msg.body() == null ? "" : msg.body().trim();
        if (subject.isEmpty() && body.isEmpty()) {
            return "";
        }
        return body.isEmpty() ? subject : subject + " " + body;
    }

    private Set<String> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new HashSet<>();
        }
        try {
            return mapper.readValue(cursor, new TypeReference<Set<String>>() {
            });
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private String encodeCursor(Set<String> keys) {
        try {
            return mapper.writeValueAsString(keys);
        } catch (Exception e) {
            return "[]";
        }
    }
}
