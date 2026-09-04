package com.memu.feedback;

import com.memu.common.Result;
import com.memu.feedback.dto.FeedbackRequest;
import com.memu.feedback.dto.FeedbackResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 反馈入口。前端建议卡片的「采纳 / 稍后 / 忽略」都走这里。
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService svc;

    public FeedbackController(FeedbackService svc) {
        this.svc = svc;
    }

    @PostMapping("/feedback")
    public Result<FeedbackResponse> feedback(@Valid @RequestBody FeedbackRequest req) {
        return Result.ok(svc.feedback(req));
    }
}
