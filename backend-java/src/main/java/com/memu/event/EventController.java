package com.memu.event;

import com.memu.common.Result;
import com.memu.event.dto.IngestRequest;
import com.memu.event.dto.IngestResponse;
import com.memu.event.dto.RetrieveResponse;
import com.memu.event.dto.Subgraph;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 事件采集与检索入口。
 */
@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService svc;

    public EventController(EventService svc) {
        this.svc = svc;
    }

    @PostMapping("/events")
    public Result<IngestResponse> ingest(@Valid @RequestBody IngestRequest req) {
        return Result.ok(svc.ingest(req));
    }

    @GetMapping("/retrieve")
    public Result<RetrieveResponse> retrieve(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int topK) {
        return Result.ok(svc.retrieve(q, topK));
    }

    @GetMapping("/graph/subgraph")
    public Result<Subgraph> subgraph(@RequestParam(defaultValue = "2") int depth) {
        return Result.ok(svc.subgraph(depth));
    }
}
