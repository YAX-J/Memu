package com.memu.event.dto;

import java.util.List;

/**
 * 对齐内核 POST /kernel/ingest 响应。
 */
public record IngestResponse(
        String eventId,
        List<EntityOut> entities,
        List<RelationOut> relations,
        double confidence,
        String llmUsed
) {
}
