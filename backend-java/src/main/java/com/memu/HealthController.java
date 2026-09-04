package com.memu;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memu.kernel.KernelClient;

/**
 * 健康检查。kernel=true 表示 Python AI 内核可达。
 * Tauri 侧据此判断链路是否打通。
 */
@RestController
public class HealthController {

    private final KernelClient kernelClient;

    public HealthController(KernelClient kernelClient) {
        this.kernelClient = kernelClient;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        boolean kernelUp;
        try {
            kernelUp = kernelClient.isHealthy();
        } catch (Exception e) {
            kernelUp = false;
        }
        return Map.of(
                "backend", "ok",
                "kernel", kernelUp
        );
    }
}
