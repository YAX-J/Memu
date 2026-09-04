package com.memu.integration;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成同步入口：手动触发（便于联调），平时由 IntegrationScheduler 定时跑。
 */
@RestController
@RequestMapping("/api/integration")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        int count = integrationService.syncAll();
        return Map.of("ok", true, "ingested", count);
    }
}
