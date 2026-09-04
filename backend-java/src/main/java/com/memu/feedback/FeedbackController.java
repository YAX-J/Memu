package com.memu.feedback;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.memu.kernel.dto.FeedbackDtos;
import com.memu.suggestion.SuggestionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 反馈入口。前端建议卡片的「采纳 / 稍后 / 忽略」都走这里：
 * suggestionId 是 Java 侧建议实例 ID，内部换成 habitId 回传内核。
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final SuggestionService suggestions;

    public FeedbackController(SuggestionService suggestions) {
        this.suggestions = suggestions;
    }

    @PostMapping("/feedback")
    public FeedbackDtos.FeedbackResponse feedback(@Valid @RequestBody FeedbackPayload payload) {
        FeedbackDtos.FeedbackResponse resp = suggestions.act(payload.suggestionId(), payload.action());
        if (resp == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "未找到建议: " + payload.suggestionId());
        }
        return resp;
    }

    public record FeedbackPayload(
            @NotBlank String suggestionId,
            @NotBlank String action
    ) {
    }
}
