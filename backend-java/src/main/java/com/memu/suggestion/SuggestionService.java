package com.memu.suggestion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.memu.kernel.KernelClient;
import com.memu.kernel.dto.FeedbackDtos;
import com.memu.kernel.dto.SuggestDtos;

/**
 * 主动建议业务编排。
 *
 * 关键约定：内核返回的 SuggestionDto.id 即 habitId；
 * Java 侧另生成建议实例（UUID），反馈时用 suggestionId 查出 habitId 回传内核。
 * 建议实例与用户动作由 Java 保存（SuggestionRepository 落盘），
 * 习惯权重由内核维护 —— 两边职责不重叠。
 */
@Service
public class SuggestionService {

    private final KernelClient kernel;
    private final SuggestionBroadcaster broadcaster;
    private final SuggestionRepository repo;

    public SuggestionService(
            KernelClient kernel,
            SuggestionBroadcaster broadcaster,
            SuggestionRepository repo) {
        this.kernel = kernel;
        this.broadcaster = broadcaster;
        this.repo = repo;
    }

    /**
     * 拉取候选建议：内核 → 领域模型 → 存储 → 广播。
     * 同一 habit 已有 PENDING 实例时不重复生成。
     */
    public synchronized List<Suggestion> refresh() {
        SuggestDtos.SuggestResponse resp = kernel.suggest();
        List<Suggestion> out = new ArrayList<>();
        List<SuggestDtos.SuggestionDto> dtos = resp.suggestions() == null
                ? List.of() : resp.suggestions();

        for (SuggestDtos.SuggestionDto dto : dtos) {
            Suggestion existing = repo.all().values().stream()
                    .filter(s -> dto.id().equals(s.habitId())
                            && s.status() == SuggestionStatus.PENDING)
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                out.add(existing);
                continue;
            }
            Suggestion s = Suggestion.pending(dto);
            repo.put(s);
            out.add(s);
        }
        broadcaster.broadcast(out);
        return out;
    }

    public List<Suggestion> listPending() {
        return repo.all().values().stream()
                .filter(s -> s.status() == SuggestionStatus.PENDING)
                .toList();
    }

    /**
     * 反馈闭环：suggestionId → habitId → 内核调整习惯权重。
     * 建议不存在时返回 null（controller 转 404）。
     */
    public FeedbackDtos.FeedbackResponse act(String suggestionId, String action) {
        Suggestion s = repo.get(suggestionId);
        if (s == null) {
            return null;
        }
        FeedbackDtos.FeedbackResponse resp = kernel.feedback(
                new FeedbackDtos.FeedbackRequest(s.habitId(), action, OffsetDateTime.now()));

        SuggestionStatus status = switch (action) {
            case "accepted" -> SuggestionStatus.ACCEPTED;
            case "snoozed" -> SuggestionStatus.SNOOZED;
            default -> SuggestionStatus.DISMISSED;
        };
        repo.put(s.withStatus(status));
        return resp;
    }
}
