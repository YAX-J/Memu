package com.memu.event.dto;

import java.util.List;

/**
 * 对齐内核 POST /kernel/retrieve 响应。
 */
public record RetrieveResponse(List<RetrieveResult> results) {
}
