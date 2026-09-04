package com.memu.event;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.memu.kernel.KernelClient;
import com.memu.kernel.KernelUnavailableException;
import com.memu.kernel.dto.IngestDtos;
import com.memu.kernel.dto.RetrieveDtos;
import com.memu.suggestion.SuggestionService;

/**
 * 事件业务编排：Java 侧生成 eventId，经内核抽取入图。
 *
 * 事件驱动触发：新事件入图后立即扫一遍习惯（同步，图谱小、扫描快），
 * 命中已有 Habit 模式就即时推送建议，不必等 5 分钟定时轮询 ——
 * 这是「双触发」里除定时扫描之外的另一条腿。扫描失败不阻断 ingest，
 * 由 SuggestionScheduler 定时兜底。
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final KernelClient kernel;
    private final SuggestionService suggestions;

    public EventService(KernelClient kernel, SuggestionService suggestions) {
        this.kernel = kernel;
        this.suggestions = suggestions;
    }

    public IngestDtos.IngestResponse ingest(EventPayload payload) {
        String type = (payload.type() == null || payload.type().isBlank())
                ? "note" : payload.type();
        String eventId = (payload.eventId() == null || payload.eventId().isBlank())
                ? UUID.randomUUID().toString() : payload.eventId();
        IngestDtos.IngestRequest req = new IngestDtos.IngestRequest(
                eventId,
                payload.source(),
                type,
                payload.occurredAt(),
                payload.rawText(),
                payload.meta()
        );
        IngestDtos.IngestResponse resp = kernel.ingest(req);

        triggerSuggestionRefresh();
        return resp;
    }

    public RetrieveDtos.RetrieveResponse retrieve(String query, int topK) {
        return kernel.retrieve(new RetrieveDtos.RetrieveRequest(query, topK, null));
    }

    public Map<String, Object> subgraph(String center, int depth) {
        return kernel.subgraph(center, depth);
    }

    private void triggerSuggestionRefresh() {
        try {
            suggestions.refresh();
        } catch (KernelUnavailableException e) {
            // 内核刚 ingest 成功，这里通常不会不可达；万一失败交给定时器兜底
            log.debug("事件驱动扫描跳过（内核不可达）: {}", e.getMessage());
        }
    }
}
