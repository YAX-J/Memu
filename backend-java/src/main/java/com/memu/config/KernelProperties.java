package com.memu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python 内核连接配置（application.yml: memu.kernel.*）。
 * 嵌套结构对齐 WebClientConfig 的读取方式，缺省值兜底防止误配 0 超时。
 */
@ConfigurationProperties(prefix = "memu")
public record KernelProperties(Kernel kernel) {

    public record Kernel(String baseUrl, Integer connectTimeoutMs, Integer readTimeoutMs) {
        public Kernel {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://127.0.0.1:8765";
            }
            if (connectTimeoutMs == null || connectTimeoutMs <= 0) {
                connectTimeoutMs = 3000;
            }
            if (readTimeoutMs == null || readTimeoutMs <= 0) {
                readTimeoutMs = 60000;
            }
        }
    }
}
