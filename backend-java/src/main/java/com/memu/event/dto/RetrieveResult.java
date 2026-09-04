package com.memu.event.dto;

/**
 * 对齐内核 /kernel/retrieve 响应中的单条结果。
 */
public record RetrieveResult(String nodeId, String type, double score, String snippet) {
}
