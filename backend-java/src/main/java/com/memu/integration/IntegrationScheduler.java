package com.memu.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集成同步定时器：周期性把日历/邮件等外部源的新事件拉进图谱。
 */
@Component
public class IntegrationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntegrationScheduler.class);

    private final IntegrationService integrationService;

    public IntegrationScheduler(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @Scheduled(
            fixedDelayString = "${memu.integration.poll-interval-ms:300000}",
            initialDelay = 15_000
    )
    public void sync() {
        try {
            int count = integrationService.syncAll();
            if (count > 0) {
                log.info("集成同步新增 {} 条事件", count);
            }
        } catch (Exception e) {
            log.error("集成同步失败", e);
        }
    }
}
