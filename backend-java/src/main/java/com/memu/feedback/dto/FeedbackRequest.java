package com.memu.feedback.dto;

import java.time.OffsetDateTime;

/**
 * 对齐内核 POST /kernel/feedback 请求体。
 */
public record FeedbackRequest(String suggestionId, String action, OffsetDateTime at) {
}
