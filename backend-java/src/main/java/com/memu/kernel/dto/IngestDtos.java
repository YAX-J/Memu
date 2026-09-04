package com.memu.kernel.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * /kernel/ingest 契约。字段名必须与 Python api/schemas.py 完全一致。
 */
public final class IngestDtos {

    public record IngestRequest(
            String eventId,
            String source,
            String type,
            OffsetDateTime occurredAt,
            String rawText,
            Map<String, Object> meta
    ) {}

    public record IngestResponse(
            String eventId,
            List<EntityOut> entities,
            List<RelationOut> relations,
            double confidence,
            String llmUsed
    ) {}

    public record EntityOut(String id, String type, String name) {}

    /** 线上 JSON 用 from / to，与 Python 的 serialization_alias 对齐 */
    public record RelationOut(String from, String to, String type) {}

    private IngestDtos() {}
}
