package com.memu.suggestion;

import com.memu.client.KernelClient;
import com.memu.suggestion.dto.SuggestResponse;
import org.springframework.stereotype.Service;

/**
 * 主动建议业务编排：调内核 /kernel/suggest 拿候选 → 推送前端。
 * P1 会在此加入定时调度（@Scheduled）与置信度过滤的二次校验。
 */
@Service
public class SuggestionService {

    private final KernelClient kernel;
    private final SuggestionBroadcaster broadcaster;

    public SuggestionService(KernelClient kernel, SuggestionBroadcaster broadcaster) {
        this.kernel = kernel;
        this.broadcaster = broadcaster;
    }

    public SuggestResponse refresh() {
        SuggestResponse resp = kernel.suggest();
        broadcaster.broadcast(resp);
        return resp;
    }
}
