package com.memu.client;

import com.memu.config.KernelProperties;
import com.memu.event.dto.*;
import com.memu.feedback.dto.FeedbackRequest;
import com.memu.feedback.dto.FeedbackResponse;
import com.memu.suggestion.dto.SuggestResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Python AI 内核的 HTTP 客户端。Java 后端对内核的全部访问都收口在这里。
 *
 * 契约见 docs/DESIGN.md 第 4 节。每个方法一一对应内核接口。
 */
@Component
public class KernelClient {

    private final RestClient http;
    private final KernelProperties props;

    public KernelClient(@Qualifier("kernelRestClient") RestClient http, KernelProperties props) {
        this.http = http;
        this.props = props;
    }

    public IngestResponse ingest(IngestRequest req) {
        return http.post()
                .uri("/kernel/ingest")
                .body(req)
                .retrieve()
                .body(IngestResponse.class);
    }

    public RetrieveResponse retrieve(String query, int topK) {
        return http.post()
                .uri("/kernel/retrieve")
                .body(new RetrieveRequest(query, topK, null))
                .retrieve()
                .body(RetrieveResponse.class);
    }

    public SuggestResponse suggest() {
        return http.post()
                .uri("/kernel/suggest")
                .retrieve()
                .body(SuggestResponse.class);
    }

    public FeedbackResponse feedback(FeedbackRequest req) {
        return http.post()
                .uri("/kernel/feedback")
                .body(req)
                .retrieve()
                .body(FeedbackResponse.class);
    }

    public Subgraph subgraph(int depth) {
        return http.get()
                .uri(builder -> builder.path("/kernel/graph/subgraph")
                        .queryParam("depth", depth)
                        .build())
                .retrieve()
                .body(Subgraph.class);
    }

    public String baseUrl() {
        return props.baseUrl();
    }
}
