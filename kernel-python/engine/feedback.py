"""
反馈闭环：EWMA 更新习惯权重，连续忽略达到阈值则静默该 pattern。

这条回路是**必需件不是可选项**——没有它，主动式助手会在几周内被用户关掉通知。
设计见 docs/DESIGN.md 第 5.4 节。
"""

from __future__ import annotations

from datetime import datetime

import kuzu

from config import settings
from domain import FEEDBACK_REWARD
from graph import repo


def apply_feedback(
    conn: kuzu.Connection, habit_id: str, action: str, now: datetime
) -> tuple[float, bool] | None:
    """
    返回 (new_feedback_score, muted)；习惯不存在时返回 None。

    EWMA 作用在独立的 feedbackScore 字段上（设计 5.2），而非 confidence：
    confidence 是每次规律扫描时由 consistency/freq/recency + feedbackScore
    现算的，不能存长期反馈状态，否则下次 refresh 会被统计分覆盖、权重回流失效。

    action 语义：
      accepted  —— 采纳：加权，清零忽略计数，解除静默
      snoozed   —— 延后：轻微加权，清零计数，但不解除已有静默
      dismissed —— 忽略：降权，计数 +1，达到阈值则静默
    """
    habit = repo.get_habit(conn, habit_id)
    if habit is None:
        return None

    reward = FEEDBACK_REWARD.get(action, 0.0)
    alpha = settings.feedback_alpha
    new_feedback_score = (1 - alpha) * habit.feedbackScore + alpha * reward

    streak = habit.dismissStreak
    muted = habit.muted

    if action == "dismissed":
        streak += 1
        if streak >= settings.dismiss_mute_count:
            muted = True
    elif action == "accepted":
        streak = 0
        muted = False
    else:  # snoozed
        streak = 0

    repo.update_habit_feedback(conn, habit_id, new_feedback_score, muted, streak, now)
    return new_feedback_score, muted
