package com.memu.suggestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.memu.kernel.KernelUnavailableException;

/**
 * 定时向内核拉取候选建议。
 */
@Component
public class SuggestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SuggestionScheduler.class);

    private final SuggestionService suggestionService;

    public SuggestionScheduler(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * 内核未就绪时只告警不抛出 —— 调度线程一旦抛异常，
     * Spring 会取消该任务的后续调度，之后内核恢复了也不会再拉。
     */
    @Scheduled(
            fixedDelayString = "${memu.suggest.poll-interval-ms:300000}",
            initialDelay = 10_000
    )
    public void poll() {
        try {
            int count = suggestionService.refresh().size();
            if (count > 0) {
                log.info("新增 {} 条主动建议", count);
            }
        } catch (KernelUnavailableException e) {
            log.warn("Python AI 内核不可达，跳过本轮建议拉取");
        } catch (Exception e) {
            log.error("拉取建议失败", e);
        }
    }
}
