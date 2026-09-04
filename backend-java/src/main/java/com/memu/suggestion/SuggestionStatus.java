package com.memu.suggestion;

/**
 * 建议生命周期状态。动作语义与内核侧 engine/feedback.py 保持一致。
 */
public enum SuggestionStatus {
    PENDING,
    ACCEPTED,
    SNOOZED,
    DISMISSED
}
