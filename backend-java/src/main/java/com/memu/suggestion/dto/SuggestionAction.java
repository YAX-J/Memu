package com.memu.suggestion.dto;

import java.util.Map;

/**
 * 对齐内核 /kernel/suggest 响应中的建议动作。
 */
public record SuggestionAction(String type, Map<String, Object> payload) {
}
