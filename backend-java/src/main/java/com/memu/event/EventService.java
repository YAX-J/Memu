package com.memu.event;

import com.memu.client.KernelClient;
import com.memu.event.dto.IngestRequest;
import com.memu.event.dto.IngestResponse;
import com.memu.event.dto.RetrieveResponse;
import com.memu.event.dto.Subgraph;
import org.springframework.stereotype.Service;

/**
 * 事件业务编排。当前直接转发内核；P1 起这里会接入事件总线、
 * 异步落库、采集侧权限过滤等编排逻辑。
 */
@Service
public class EventService {

    private final KernelClient kernel;

    public EventService(KernelClient kernel) {
        this.kernel = kernel;
    }

    public IngestResponse ingest(IngestRequest req) {
        // TODO(P1): 事件总线发布 + 异步抽取
        return kernel.ingest(req);
    }

    public RetrieveResponse retrieve(String query, int topK) {
        return kernel.retrieve(query, topK);
    }

    public Subgraph subgraph(int depth) {
        return kernel.subgraph(depth);
    }
}
