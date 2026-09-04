"""建议生成：把检测到的习惯翻译成人话建议。"""

from __future__ import annotations

from datetime import datetime

import kuzu

from domain import PERIOD_WORDS
from graph import repo
from util import stable_id

from .habit import detect_habits


def refresh_habits(conn: kuzu.Connection, now: datetime) -> list[dict]:
    """检测 → 落图 → 剔除已静默的。返回可推送的习惯。"""
    out: list[dict] = []
    for h in detect_habits(conn):
        hid = repo.upsert_habit(conn, h, now)
        row = repo.get_habit(conn, hid)
        if row is not None and row.muted:
            continue
        h["id"] = hid
        out.append(h)
    return out


def build_suggestions(conn: kuzu.Connection, now: datetime) -> list[dict]:
    habits = refresh_habits(conn, now)
    return [
        {
            "id": h["id"],
            "title": f"又到处理「{h['topic']}」的时候了吗？",
            "reason": (
                f"你近 {h['count']} 次大约{PERIOD_WORDS.get(h['period'], h['period'])}处理它一次"
                f"（稳定性 {h['consistency']}），预测下次在 {h['nextAt']:%m-%d %H:%M}"
            ),
            "confidence": h["confidence"],
            "actions": [{"type": "open_timeline", "payload": {"topic": h["topic"]}}],
        }
        for h in habits
    ]
