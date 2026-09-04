package com.memu.feedback.dto;

/**
 * 对齐内核 POST /kernel/feedback 响应。
 */
public record FeedbackResponse(boolean ok, boolean patternMuted, double newWeight) {
}
