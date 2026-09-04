package com.memu.kernel.dto;

import java.util.List;
import java.util.Map;

/**
 * /kernel/suggest 契约。
 *
 * 注意：Suggestion.id 就是内核侧的 habitId，反馈时原样回传。
 * 建议实例本身由 Java 保存，内核只维护习惯权重。
 */
public final class SuggestDtos {

    public record SuggestResponse(List<SuggestionDto> suggestions, boolean coldStart) {}

    public record SuggestionDto(
            String id,
            String title,
            String reason,
            double confidence,
            List<SuggestActionDto> actions
    ) {}

    public record SuggestActionDto(String type, Map<String, Object> payload) {}

    private SuggestDtos() {}
}
