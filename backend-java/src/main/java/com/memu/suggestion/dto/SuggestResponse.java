package com.memu.suggestion.dto;

import java.util.List;

/**
 * 对齐内核 POST /kernel/suggest 响应。
 */
public record SuggestResponse(List<Suggestion> suggestions, boolean coldStart) {
}
