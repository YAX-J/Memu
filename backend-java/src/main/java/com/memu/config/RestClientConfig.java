package com.memu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 调用 Python AI 内核的 HTTP 客户端。
 * 用 RestClient（Spring 6.1 阻塞式），不引入 WebFlux，符合 MVC 风格。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient kernelRestClient(KernelProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }
}
