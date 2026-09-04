package com.memu.integration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.memu.event.EventPayload;
import com.memu.event.EventService;

/**
 * 集成编排：遍历所有 IntegrationAdapter，把拉取到的事件统一转成
 * EventPayload 送事件总线（EventService.ingest），游标按 source 存内存。
 *
 * 单个适配器失败不阻断其它适配器；日历/邮件等外部源都是「尽力而为」的输入，
 * 拉不到不报错、只降级跳过。
 */
@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final List<IntegrationAdapter> adapters;
    private final EventService eventService;
    private final Map<String, String> cursors = new ConcurrentHashMap<>();

    public IntegrationService(List<IntegrationAdapter> adapters, EventService eventService) {
        this.adapters = adapters;
        this.eventService = eventService;
    }

    /** 同步所有适配器，返回本次新入图的事件数。 */
    public synchronized int syncAll() {
        int total = 0;
        for (IntegrationAdapter adapter : adapters) {
            String cursor = cursors.getOrDefault(adapter.source(), "");
            try {
                IntegrationAdapter.SyncResult res = adapter.fetchIncremental(cursor);
                for (IntegrationAdapter.EventItem item : res.items()) {
                    eventService.ingest(new EventPayload(
                            adapter.source(),
                            item.type(),
                            item.rawText(),
                            item.occurredAt(),
                            item.meta(),
                            (String) item.meta().getOrDefault("eventId", null)
                    ));
                    total++;
                }
                cursors.put(adapter.source(), res.nextCursor());
            } catch (Exception e) {
                log.warn("适配器 {} 同步失败: {}", adapter.source(), e.getMessage());
            }
        }
        return total;
    }
}
