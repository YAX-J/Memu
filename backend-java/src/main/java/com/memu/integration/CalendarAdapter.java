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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 日历适配器（本地 ICS 文件版）。
 *
 * P2 第一版只读本地 ICS 文件（任何日历都能「导出 .ics」），不依赖 OAuth/系统日历，
 * 先跑通「日程 → 事件入图 → 抽取 → 习惯/建议」这条链路。云端日历（iCal URL）留后续。
 *
 * 增量：游标记录已处理的 UID 集合（JSON 序列化），下次只取新出现的 UID。
 */
@Component
public class CalendarAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(CalendarAdapter.class);

    private final Path icsPath;
    private final ObjectMapper mapper;

    public CalendarAdapter(
            @Value("${memu.calendar.ics-path:}") String icsPath,
            ObjectMapper mapper) {
        this.icsPath = (icsPath == null || icsPath.isBlank()) ? null : Path.of(icsPath);
        this.mapper = mapper;
    }

    @Override
    public String source() {
        return "calendar";
    }

    @Override
    public SyncResult fetchIncremental(String fromCursor) {
        if (icsPath == null || !Files.isRegularFile(icsPath)) {
            // 未配置或文件不存在：静默跳过，不阻断其它适配器
            return new SyncResult(List.of(), fromCursor);
        }

        String icsText;
        try {
            icsText = Files.readString(icsPath);
        } catch (IOException e) {
            log.warn("读取日历文件失败 {}: {}", icsPath, e.getMessage());
            return new SyncResult(List.of(), fromCursor);
        }

        List<IcsParser.IcsEvent> events = IcsParser.parse(icsText);
        Set<String> seen = decodeCursor(fromCursor);

        List<EventItem> items = new ArrayList<>();
        Set<String> newSeen = new TreeSet<>(seen);
        for (IcsParser.IcsEvent ev : events) {
            String uid = ev.uid();
            if (uid == null || uid.isBlank() || newSeen.contains(uid)) {
                continue;
            }
            newSeen.add(uid);

            String rawText = buildRawText(ev);
            if (rawText.isBlank()) {
                continue;
            }
            Map<String, Object> meta = Map.of(
                    "uid", uid,
                    "end", ev.end() == null ? "" : ev.end().toString(),
                    "eventId", "calendar:" + uid
            );
            items.add(new EventItem("meeting", rawText, ev.start(), meta));
        }

        String nextCursor = encodeCursor(newSeen);
        if (!items.isEmpty()) {
            log.info("日历同步：新增 {} 条日程", items.size());
        }
        return new SyncResult(items, nextCursor);
    }

    private String buildRawText(IcsParser.IcsEvent ev) {
        String summary = ev.summary() == null ? "" : ev.summary().trim();
        String desc = ev.description() == null ? "" : ev.description().trim();
        if (summary.isEmpty() && desc.isEmpty()) {
            return "";
        }
        return desc.isEmpty() ? summary : summary + " " + desc;
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

    private String encodeCursor(Set<String> uids) {
        try {
            return mapper.writeValueAsString(uids);
        } catch (Exception e) {
            return "[]";
        }
    }
}
