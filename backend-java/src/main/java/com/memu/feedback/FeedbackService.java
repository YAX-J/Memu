package com.memu.feedback;

import com.memu.client.KernelClient;
import com.memu.feedback.dto.FeedbackRequest;
import com.memu.feedback.dto.FeedbackResponse;
import org.springframework.stereotype.Service;

/**
 * 反馈闭环。用户采纳/忽略回流到内核做 EWMA 权重更新与静默判定。
 * P1 会在此记录本地反馈日志用于离线分析。
 */
@Service
public class FeedbackService {

    private final KernelClient kernel;

    public FeedbackService(KernelClient kernel) {
        this.kernel = kernel;
    }

    public FeedbackResponse feedback(FeedbackRequest req) {
        if (req.at() == null) {
            req = new FeedbackRequest(req.suggestionId(), req.action(),
                    java.time.OffsetDateTime.now());
        }
        return kernel.feedback(req);
    }
}
