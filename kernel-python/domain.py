"""领域词汇表与常量。抽取、落图、引擎共用，避免各模块各写一份。"""

from __future__ import annotations

# 允许 LLM 输出的实体类型（同时是图谱节点表名，落图时用于白名单校验）
ENTITY_TYPES = ("Person", "Project", "Task", "Topic")

# 允许 LLM 输出的关系类型
RELATION_TYPES = ("PARTICIPATES_IN", "RELATES_TO", "BELONGS_TO")

# 反馈 → 奖励值（EWMA 用）
FEEDBACK_REWARD: dict[str, float] = {
    "accepted": 1.0,
    "snoozed": 0.5,
    "dismissed": 0.0,
}

# 周期判定阈值（秒）
PERIOD_DAY = 86400.0
PERIOD_BOUNDS: tuple[tuple[str, float], ...] = (
    ("日", 1.5 * PERIOD_DAY),
    ("周", 10 * PERIOD_DAY),
    ("月", 45 * PERIOD_DAY),
)

# 周期标签 → 自然语言（用于建议文案，避免「又到日期了」这类拼接歧义）
PERIOD_WORDS: dict[str, str] = {
    "日": "每天",
    "周": "每周",
    "月": "每月",
}
