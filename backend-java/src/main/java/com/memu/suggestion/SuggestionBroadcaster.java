package com.memu.suggestion;

import com.memu.suggestion.dto.SuggestResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 主动建议的 WebSocket 推送器。P1 定时触发引擎产出建议后，
 * 通过这里向 /topic/suggestions 下发，前端实时收到卡片。
 */
@Component
public class SuggestionBroadcaster {

    private final SimpMessagingTemplate messaging;

    public SuggestionBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void broadcast(SuggestResponse resp) {
        messaging.convertAndSend("/topic/suggestions", resp);
    }
}
