package com.memu.kernel.dto;

import java.util.List;

/**
 * /kernel/retrieve 契约。
 */
public final class RetrieveDtos {

    public record RetrieveRequest(String query, Integer topK, List<String> timeRange) {}

    public record RetrieveResponse(List<RetrieveResult> results) {}

    public record RetrieveResult(String nodeId, String type, double score, String snippet) {}

    private RetrieveDtos() {}
}
