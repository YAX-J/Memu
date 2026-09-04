package com.memu.event.dto;

import java.util.List;
import java.util.Map;

/**
 * 对齐内核 GET /kernel/graph/subgraph 响应，供前端图谱可视化。
 */
public record Subgraph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
}
