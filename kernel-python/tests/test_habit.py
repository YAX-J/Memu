"""主动引擎：习惯评分 + 周期判定测试。"""

from datetime import datetime, timedelta, timezone

from engine.habit import period_label, score_habit


def _times(days_ago_list):
    now = datetime.now(timezone.utc)
    return [now - timedelta(days=d) for d in days_ago_list]


def test_daily_habit_confident():
    # 4 条间隔正好 1 天 → 周期「日」，置信度 ≈ 0.807（手算值）
    r = score_habit(_times([3, 2, 1, 0]))
    assert r is not None
    assert r["period"] == "日"
    assert abs(r["consistency"] - 1.0) < 1e-3
    assert abs(r["confidence"] - 0.807) < 0.005


def test_feedback_score_boosts_confidence():
    # feedbackScore 参与 0.15 权重项：采纳加分会抬高置信度
    times = _times([3, 2, 1, 0])
    neutral = score_habit(times, 0.5)
    boosted = score_habit(times, 1.0)
    assert boosted["confidence"] > neutral["confidence"]


def test_insufficient_samples():
    assert score_habit(_times([1, 0])) is None


def test_period_label():
    assert period_label(86400) == "日"
    assert period_label(86400 * 7) == "周"
    assert period_label(86400 * 30) == "月"
    assert period_label(86400 * 100) is None
