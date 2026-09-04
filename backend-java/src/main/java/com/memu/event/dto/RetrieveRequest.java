package com.memu.event.dto;

import java.util.List;

/**
 * 对齐内核 POST /kernel/retrieve 请求体。
 */
public record RetrieveRequest(String query, Integer topK, List<String> timeRange) {
}
