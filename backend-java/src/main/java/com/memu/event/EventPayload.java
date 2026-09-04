package com.memu.event;

import java.time.OffsetDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * Tauri 侧采集到的原始事件。
 *
 * eventId 可选：外部源（日历/邮件）传稳定 id 以实现幂等（同一日程/邮件
 * 重复同步不重复入图）；不传则由 EventService 生成随机 UUID。
 */
public record EventPayload(
        @NotBlank(message = "source 不能为空") String source,
        String type,
        @NotBlank(message = "rawText 不能为空") String rawText,
        OffsetDateTime occurredAt,
        Map<String, Object> meta,
        String eventId
) {}
