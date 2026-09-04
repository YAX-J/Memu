package com.memu.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * 调用 Python AI 内核的 WebClient。
 *
 * 这里刻意用 WebClient 而非 RestTemplate：RestTemplate 已进入维护模式，
 * 且后续若接流式抽取，响应式的 WebClient 才能平滑支持。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient kernelWebClient(KernelProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.kernel().connectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.kernel().readTimeoutMs()));

        return WebClient.builder()
                .baseUrl(props.kernel().baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
