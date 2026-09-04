package com.memu.kernel;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.memu.kernel.dto.FeedbackDtos;
import com.memu.kernel.dto.IngestDtos;
import com.memu.kernel.dto.RetrieveDtos;
import com.memu.kernel.dto.SuggestDtos;

/**
 * 调用 Python AI 内核的**唯一出口**。
 * 业务层不允许自己拼 HTTP 请求调内核，否则契约一旦变更会散落各处。
 */
@Component
public class KernelClient {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient client;

    public KernelClient(WebClient kernelWebClient) {
        this.client = kernelWebClient;
    }

    public IngestDtos.IngestResponse ingest(IngestDtos.IngestRequest request) {
        return post("/kernel/ingest", request, IngestDtos.IngestResponse.class);
    }

    public RetrieveDtos.RetrieveResponse retrieve(RetrieveDtos.RetrieveRequest request) {
        return post("/kernel/retrieve", request, RetrieveDtos.RetrieveResponse.class);
    }

    public SuggestDtos.SuggestResponse suggest() {
        return client.post()
                .uri("/kernel/suggest")
                .retrieve()
                .bodyToMono(SuggestDtos.SuggestResponse.class)
                .block(BLOCK_TIMEOUT);
    }

    public FeedbackDtos.FeedbackResponse feedback(FeedbackDtos.FeedbackRequest request) {
        return post("/kernel/feedback", request, FeedbackDtos.FeedbackResponse.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> subgraph(String center, int depth) {
        String uri = (center == null || center.isBlank())
                ? "/kernel/graph/subgraph?depth=" + depth
                : "/kernel/graph/subgraph?center=" + center + "&depth=" + depth;
        return client.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .block(BLOCK_TIMEOUT);
    }

    public boolean isHealthy() {
        try {
            return client.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(body -> Boolean.TRUE.equals(body.get("ok")))
                    .block(Duration.ofSeconds(3));
        } catch (WebClientRequestException e) {
            return false;
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return client.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block(BLOCK_TIMEOUT);
        } catch (WebClientRequestException e) {
            throw new KernelUnavailableException("无法连接 Python AI 内核: " + path, e);
        }
    }
}
