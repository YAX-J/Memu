package com.memu.suggestion.dto;

import java.util.List;

/**
 * 对齐内核 /kernel/suggest 响应中的单条建议。
 */
public record Suggestion(
        String id,
        String title,
        String reason,
        double confidence,
        List<SuggestionAction> actions
) {
}
