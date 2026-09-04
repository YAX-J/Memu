package com.memu.suggestion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主动建议入口。
 * - GET  /api/suggestions         拉取当前待处理建议（HTTP 拉模式，P0）
 * - POST /api/suggestions/refresh 重新扫描并广播（P1 定时器走同一服务方法）
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService svc;

    public SuggestionController(SuggestionService svc) {
        this.svc = svc;
    }

    @PostMapping("/refresh")
    public SuggestionsView refresh() {
        return view(svc.refresh());
    }

    @GetMapping
    public SuggestionsView list() {
        return view(svc.listPending());
    }

    private SuggestionsView view(List<Suggestion> list) {
        // 前端只认 { suggestions, coldStart } 结构
        return new SuggestionsView(list, list.isEmpty());
    }

    public record SuggestionsView(List<Suggestion> suggestions, boolean coldStart) {
    }
}
