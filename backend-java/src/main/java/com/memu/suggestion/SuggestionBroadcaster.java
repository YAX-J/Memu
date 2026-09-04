package com.memu.suggestion;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 主动建议的 WebSocket 推送器：SuggestionScheduler 定时拉取后，
 * 通过这里向 /topic/suggestions 下发，前端实时收到卡片。
 */
@Component
public class SuggestionBroadcaster {

    private final SimpMessagingTemplate messaging;

    public SuggestionBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void broadcast(List<Suggestion> suggestions) {
        messaging.convertAndSend("/topic/suggestions", suggestions);
    }
}
