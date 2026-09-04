"""
规律学习：从事件时序中检测周期性习惯。

关键设计：这里**不调 LLM**，纯图查询 + 统计。
高频的规律发现若走模型，token 成本会吃掉"运行成本更低"这个卖点。
公式见 docs/DESIGN.md 第 5.1 节。
"""

from __future__ import annotations

import math
from datetime import datetime, timedelta

import kuzu

from config import settings
from domain import PERIOD_BOUNDS
from graph import repo
from util import ensure_aware, stable_id

FREQ_SATURATION = 21.0  # 出现 20 次即视为频次饱和


def period_label(seconds: float) -> str | None:
    """把中位间隔归一到 日/周/月；超出月量程认为不成周期。"""
    for label, bound in PERIOD_BOUNDS:
        if seconds < bound:
            return label
    return None


def score_habit(times: list[datetime], feedback_score: float = 0.5) -> dict | None:
    """
    输入同一 (事件类型, 主题) 下的发生时间序列，输出习惯评分。
    样本不足、间隔为零或不成周期时返回 None。

    feedback_score 为该 pattern 的反馈回流权重（EWMA），冷启动无样本时取中性值。
    """
    if len(times) < settings.cold_start_min_events:
        return None

    times = sorted(t for t in (ensure_aware(t) for t in times) if t is not None)
    if len(times) < settings.cold_start_min_events:
        return None

    intervals = [(times[i + 1] - times[i]).total_seconds() for i in range(len(times) - 1)]
    mean = sum(intervals) / len(intervals)
    if mean <= 0:
        return None

    std = (sum((x - mean) ** 2 for x in intervals) / len(intervals)) ** 0.5
    consistency = max(0.0, 1.0 - std / mean)

    freq = min(1.0, math.log(1 + len(times)) / math.log(FREQ_SATURATION))

    days_since = max(0, (datetime.now(times[-1].tzinfo) - times[-1]).days)
    recency = math.exp(-settings.recency_lambda * days_since)

    period_seconds = sorted(intervals)[len(intervals) // 2]
    period = period_label(period_seconds)
    if period is None:
        return None

    confidence = max(
        0.0,
        min(
            1.0,
            0.35 * consistency
            + 0.25 * freq
            + 0.25 * recency
            + 0.15 * feedback_score,
        ),
    )

    return {
        "count": len(times),
        "period": period,
        "consistency": round(consistency, 3),
        "freq": round(freq, 3),
        "recency": round(recency, 3),
        "confidence": round(confidence, 3),
        "feedbackScore": round(feedback_score, 3),
        "nextAt": times[-1] + timedelta(seconds=period_seconds),
    }


def detect_habits(conn: kuzu.Connection) -> list[dict]:
    """扫描全图，返回置信度达标的周期性习惯。"""
    habits: list[dict] = []
    for etype, topic, times in repo.iter_event_topic_groups(conn):
        pattern = f"{etype}:{topic}"
        existing = repo.get_habit(conn, stable_id("habit", pattern))
        feedback_score = (
            existing.feedbackScore if existing else settings.neutral_feedback_score
        )
        scored = score_habit(times, feedback_score)
        if not scored or scored["confidence"] < settings.suggest_threshold:
            continue
        habits.append(
            {
                "pattern": pattern,
                "topic": topic,
                "eventType": etype,
                **scored,
            }
        )
    return sorted(habits, key=lambda h: h["confidence"], reverse=True)
