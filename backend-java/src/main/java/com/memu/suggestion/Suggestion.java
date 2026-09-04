package com.memu.suggestion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memu.kernel.dto.SuggestDtos;

/**
 * 建议实例。
 *
 * 关键约定：habitId 即内核侧的 Habit 节点 ID，由内核生成并在 SuggestionDto.id 带回。
 * 建议实例与用户动作由 Java 保存，习惯权重由内核维护 —— 两边职责不重叠。
 */
public record Suggestion(
        String id,
        String habitId,
        String title,
        String reason,
        double confidence,
        List<SuggestDtos.SuggestActionDto> actions,
        SuggestionStatus status,
        Instant createdAt,
        Instant actedAt
) {

    public static Suggestion pending(SuggestDtos.SuggestionDto dto) {
        return new Suggestion(
                UUID.randomUUID().toString(),
                dto.id(),
                dto.title(),
                dto.reason(),
                dto.confidence(),
                dto.actions() == null ? List.of() : dto.actions(),
                SuggestionStatus.PENDING,
                Instant.now(),
                null
        );
    }

    public Suggestion withStatus(SuggestionStatus newStatus) {
        return new Suggestion(
                id, habitId, title, reason, confidence,
                actions, newStatus, createdAt, Instant.now()
        );
    }
}
