package com.memu.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 放行 Tauri 前端（dev server :1420）跨域访问后端 API。
 *
 * 注意：origins 在 application.yml 里配成逗号分隔的标量字符串，
 * 因为 @Value 无法把 YAML 列表绑定到 List（会抛 placeholder 解析失败）。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> origins;

    public CorsConfig(@Value("${memu.cors.origins:http://localhost:1420}") String origins) {
        this.origins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
