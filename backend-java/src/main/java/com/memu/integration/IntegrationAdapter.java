package com.memu.integration;

import java.util.List;
import java.util.Map;

/**
 * 外部数据源集成适配器抽象。所有采集来的事件最终都转成
 * 统一的事件 Map（对齐 IngestRequest）送入事件总线。
 *
 * P2 实现：日历、邮件、文件系统、浏览器活动等。
 */
public interface IntegrationAdapter {

    /** 适配器标识，如 "calendar" / "mail"。 */
    String source();

    /**
     * 拉取增量事件。fromCursor 为上次同步游标（可为空）。
     * 返回 (事件列表, 下次游标)。
     */
    SyncResult fetchIncremental(String fromCursor);

    record EventItem(String type, String rawText, java.time.OffsetDateTime occurredAt,
                     Map<String, Object> meta) {
    }

    record SyncResult(List<EventItem> items, String nextCursor) {
    }
}
