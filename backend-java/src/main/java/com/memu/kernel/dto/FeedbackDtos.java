package com.memu.kernel.dto;

import java.time.OffsetDateTime;

/**
 * /kernel/feedback 契约。
 *
 * action 语义（内核侧 engine/feedback.py）：
 *   accepted  —— 采纳：加权，清零忽略计数，解除静默
 *   snoozed   —— 延后：轻微加权，清零计数，不解除已有静默
 *   dismissed —— 忽略：降权，计数 +1，连续达阈值则静默该 pattern
 */
public final class FeedbackDtos {

    public record FeedbackRequest(String habitId, String action, OffsetDateTime at) {}

    public record FeedbackResponse(boolean ok, boolean patternMuted, double newWeight) {}

    private FeedbackDtos() {}
}
