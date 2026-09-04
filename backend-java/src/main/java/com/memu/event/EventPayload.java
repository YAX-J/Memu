package com.memu.event;

import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * Tauri 侧采集到的原始事件。
 */
public record EventPayload(
        @NotBlank(message = "source 不能为空") String source,
        String type,
        @NotBlank(message = "rawText 不能为空") String rawText,
        OffsetDateTime occurredAt,
        Map<String, Object> meta
) {}
