package com.memu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python 内核连接配置（application.yml: memu.kernel.*）。
 */
@ConfigurationProperties(prefix = "memu.kernel")
public record KernelProperties(String baseUrl) {
}
