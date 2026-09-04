"""
混合检索：向量召回（embedding 相似度）+ 图谱扩散（相邻实体）。

P0 是 CONTAINS 关键词匹配（只中字面），P1 升级为：
  1. query 向量化，与全量事件 rawText 的向量做余弦相似度排序 → 召回 topK 事件
  2. 对召回事件沿边扩散出相邻 Topic / Person，让用户看到上下文

embedding 走 embedding.py 的磁盘缓存，重复检索零重算。
"""

from __future__ import annotations

import kuzu

import embedding
from graph import repo


def _neighbor_maps(conn: kuzu.Connection) -> tuple[dict[str, list[tuple[str, str]]], dict[str, list[tuple[str, str]]]]:
    """event_id → [(id, name)] 的 Topic / Person 邻居映射。"""
    topics: dict[str, list[tuple[str, str]]] = {}
    for eid, tid, tname in repo.list_event_topic_edges(conn):
        topics.setdefault(eid, []).append((tid, tname))

    persons: dict[str, list[tuple[str, str]]] = {}
    for eid, pid, pname in repo.list_event_person_edges(conn):
        persons.setdefault(eid, []).append((pid, pname))

    return topics, persons


def vector_search(conn: kuzu.Connection, query: str, top_k: int) -> list[dict]:
    """返回 results：[{nodeId, type, score, snippet}]，事件按相似度降序，扩散实体随其后。"""
    if not query.strip():
        return []

    qvec = embedding.embed(query)
    events = repo.list_all_events(conn)
    topics, persons = _neighbor_maps(conn)

    scored: list[tuple[float, dict]] = []
    for eid, etype, raw_text, occurred_at in events:
        text = raw_text or ""
        sim = embedding.cosine(qvec, embedding.embed(text))
        if sim <= 0.0:
            continue
        scored.append(
            (
                sim,
                {
                    "nodeId": eid,
                    "type": etype or "Event",
                    "score": round(sim, 4),
                    "snippet": text[:200],
                    "_at": occurred_at,
                },
            )
        )

    scored.sort(key=lambda x: x[0], reverse=True)
    top = scored[:top_k]

    results: list[dict] = []
    seen_ids: set[str] = set()
    for sim, item in top:
        item.pop("_at", None)
        results.append(item)
        seen_ids.add(item["nodeId"])

        # 图谱扩散：召回事件的相邻 Topic / Person 追加为上下文结果
        for tid, tname in topics.get(item["nodeId"], []):
            if tid in seen_ids:
                continue
            seen_ids.add(tid)
            results.append(
                {"nodeId": tid, "type": "Topic", "score": round(sim * 0.9, 4), "snippet": tname}
            )
        for pid, pname in persons.get(item["nodeId"], []):
            if pid in seen_ids:
                continue
            seen_ids.add(pid)
            results.append(
                {"nodeId": pid, "type": "Person", "score": round(sim * 0.9, 4), "snippet": pname}
            )

    return results
