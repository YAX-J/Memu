package com.memu.suggestion;

import com.memu.common.Result;
import com.memu.suggestion.dto.SuggestResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 主动建议入口。
 * - GET  /api/suggestions       拉取当前候选（HTTP 拉模式，P0 用）
 * - POST /api/suggestions/refresh 重新扫描图谱并广播（P1 定时器也走同一服务方法）
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService svc;

    public SuggestionController(SuggestionService svc) {
        this.svc = svc;
    }

    @PostMapping("/refresh")
    public Result<SuggestResponse> refresh() {
        return Result.ok(svc.refresh());
    }

    @GetMapping
    public Result<SuggestResponse> list() {
        return Result.ok(svc.refresh());
    }
}
