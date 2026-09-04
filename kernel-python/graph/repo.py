"""
图谱仓储层：所有 Cypher 只出现在这个文件里。
上层（api / engine）不直接写 Cypher，方便后续换图库或改 schema。
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Iterator, NamedTuple

import kuzu

from domain import ENTITY_TYPES
from util import stable_id


class HabitRow(NamedTuple):
    id: str
    pattern: str
    period: str
    confidence: float
    nextAt: datetime | None
    muted: bool
    dismissStreak: int


def _table(entity_type: str) -> str:
    """表名无法参数化，必须白名单校验防注入。"""
    if entity_type not in ENTITY_TYPES:
        raise ValueError(f"非法实体类型: {entity_type}")
    return entity_type


# ---------------------------------------------------------------- 写入


def upsert_entity(conn: kuzu.Connection, etype: str, name: str, now: datetime) -> str:
    eid = stable_id(etype, name)
    name_col = "title" if etype == "Task" else "name"
    conn.execute(
        f"MERGE (n:{_table(etype)} {{id: $id}}) "
        f"ON CREATE SET n.{name_col} = $name, n.confidence = 1.0, "
        f"n.createdAt = $now, n.updatedAt = $now "
        f"ON MATCH SET n.updatedAt = $now",
        {"id": eid, "name": name, "now": now},
    )
    return eid


def upsert_event(
    conn: kuzu.Connection,
    event_id: str,
    etype: str,
    source: str,
    raw_text: str,
    occurred_at: datetime,
    now: datetime,
) -> str:
    conn.execute(
        "MERGE (e:Event {id: $id}) "
        "ON CREATE SET e.type = $type, e.source = $source, e.rawText = $rawText, "
        "e.occurredAt = $occurredAt, e.confidence = 1.0, "
        "e.createdAt = $now, e.updatedAt = $now "
        "ON MATCH SET e.updatedAt = $now",
        {
            "id": event_id,
            "type": etype,
            "source": source,
            "rawText": raw_text,
            "occurredAt": occurred_at,
            "now": now,
        },
    )
    return event_id


def link_person_to_event(conn: kuzu.Connection, person_id: str, event_id: str, now: datetime) -> None:
    conn.execute(
        "MATCH (p:Person {id: $pid}), (e:Event {id: $eid}) "
        "MERGE (p)-[r:PARTICIPATES_IN]->(e) "
        "ON CREATE SET r.weight = 1.0, r.createdAt = $now",
        {"pid": person_id, "eid": event_id, "now": now},
    )


def link_event_to_topic(conn: kuzu.Connection, event_id: str, topic_id: str, now: datetime) -> None:
    """RELATES_TO 目前只支持 Event → Topic，见 schema.cypher 末尾说明。"""
    conn.execute(
        "MATCH (e:Event {id: $eid}), (t:Topic {id: $tid}) "
        "MERGE (e)-[r:RELATES_TO]->(t) "
        "ON CREATE SET r.weight = 1.0, r.createdAt = $now",
        {"eid": event_id, "tid": topic_id, "now": now},
    )


def upsert_habit(conn: kuzu.Connection, habit: dict[str, Any], now: datetime) -> str:
    hid = stable_id("habit", habit["pattern"])
    conn.execute(
        "MERGE (h:Habit {id: $id}) "
        "ON CREATE SET h.pattern = $pattern, h.period = $period, "
        "h.confidence = $confidence, h.nextAt = $nextAt, h.muted = false, "
        "h.dismissStreak = 0, h.createdAt = $now, h.updatedAt = $now "
        "ON MATCH SET h.confidence = $confidence, h.nextAt = $nextAt, h.updatedAt = $now",
        {
            "id": hid,
            "pattern": habit["pattern"],
            "period": habit["period"],
            "confidence": habit["confidence"],
            "nextAt": habit["nextAt"],
            "now": now,
        },
    )
    return hid


def update_habit_feedback(
    conn: kuzu.Connection,
    habit_id: str,
    confidence: float,
    muted: bool,
    streak: int,
    now: datetime,
) -> None:
    conn.execute(
        "MATCH (h:Habit {id: $id}) "
        "SET h.confidence = $conf, h.muted = $muted, h.dismissStreak = $streak, h.updatedAt = $now",
        {"id": habit_id, "conf": confidence, "muted": muted, "streak": streak, "now": now},
    )


# ---------------------------------------------------------------- 读取


def iter_event_topic_groups(conn: kuzu.Connection) -> Iterator[tuple[str, str, list[datetime]]]:
    """按 (事件类型, 关联主题) 分组取时间戳序列，供规律学习使用。"""
    rows = conn.execute(
        "MATCH (e:Event)-[:RELATES_TO]->(t:Topic) "
        "RETURN e.type AS etype, t.name AS topic, collect(e.occurredAt) AS times"
    )
    while rows.has_next():
        etype, topic, times = rows.get_next()
        if not topic or not times:
            continue
        yield etype, topic, list(times)


def get_habit(conn: kuzu.Connection, habit_id: str) -> HabitRow | None:
    rows = conn.execute(
        "MATCH (h:Habit {id: $id}) "
        "RETURN h.id, h.pattern, h.period, h.confidence, h.nextAt, h.muted, h.dismissStreak",
        {"id": habit_id},
    )
    if not rows.has_next():
        return None
    r = rows.get_next()
    return HabitRow(
        id=r[0],
        pattern=r[1] or "",
        period=r[2] or "",
        confidence=float(r[3] or 0.0),
        nextAt=r[4],
        muted=bool(r[5]),
        dismissStreak=int(r[6] or 0),
    )


def list_recent_events(conn: kuzu.Connection, limit: int) -> list[tuple]:
    rows = conn.execute(
        "MATCH (e:Event) RETURN e.id, e.type, e.rawText, e.source, e.occurredAt "
        "ORDER BY e.occurredAt DESC LIMIT $limit",
        {"limit": limit},
    )
    out = []
    while rows.has_next():
        out.append(tuple(rows.get_next()))
    return out


def list_event_topic_edges(conn: kuzu.Connection) -> list[tuple]:
    rows = conn.execute(
        "MATCH (e:Event)-[:RELATES_TO]->(t:Topic) RETURN e.id, t.id, t.name"
    )
    out = []
    while rows.has_next():
        out.append(tuple(rows.get_next()))
    return out


def search_events_by_text(conn: kuzu.Connection, keyword: str, top_k: int) -> list[tuple]:
    rows = conn.execute(
        "MATCH (e:Event) WHERE e.rawText LIKE $kw "
        "RETURN e.id, e.type, e.rawText, e.occurredAt "
        "ORDER BY e.occurredAt DESC LIMIT $topK",
        {"kw": f"%{keyword}%", "topK": top_k},
    )
    out = []
    while rows.has_next():
        out.append(tuple(rows.get_next()))
    return out
