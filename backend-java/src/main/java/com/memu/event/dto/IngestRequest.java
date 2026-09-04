package com.memu.event.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 对齐内核 POST /kernel/ingest 请求体。
 */
public record IngestRequest(
        String eventId,
        String source,
        String type,
        OffsetDateTime occurredAt,
        @NotBlank String rawText,
        Map<String, Object> meta
) {
}
