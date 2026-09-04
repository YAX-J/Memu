package com.memu.event;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memu.kernel.dto.IngestDtos;
import com.memu.kernel.dto.RetrieveDtos;

import jakarta.validation.Valid;

/**
 * 事件采集与检索入口。返回体直接给前端，不再套 Result 包装。
 */
@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService svc;

    public EventController(EventService svc) {
        this.svc = svc;
    }

    /** 采集事件入口。eventId 由 Java 生成，调用方无需关心。 */
    @PostMapping("/events")
    public IngestDtos.IngestResponse ingest(@Valid @RequestBody EventPayload payload) {
        return svc.ingest(payload);
    }

    @GetMapping("/retrieve")
    public RetrieveDtos.RetrieveResponse retrieve(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int topK) {
        return svc.retrieve(q, topK);
    }

    @GetMapping("/graph/subgraph")
    public Map<String, Object> subgraph(
            @RequestParam(required = false) String center,
            @RequestParam(defaultValue = "2") int depth) {
        return svc.subgraph(center, depth);
    }
}
