"""
契约路由。这里只做参数校验与流程编排，业务逻辑一律下沉到
engine / extract / graph 三层，路由里不写 Cypher、不写算法。
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException

from config import settings
from engine import feedback as feedback_engine
from engine.suggester import build_suggestions
from extract.extractor import extract
from graph import repo
from graph.db import get_connection
from util import now, stable_id

from .schemas import (
    EntityOut,
    FeedbackRequest,
    FeedbackResponse,
    IngestRequest,
    IngestResponse,
    RelationOut,
    RetrieveRequest,
    RetrieveResponse,
    RetrieveResult,
    Suggestion,
    SuggestResponse,
)

router = APIRouter()


@router.get("/health")
def health() -> dict:
    return {
        "ok": True,
        "service": "memu-kernel",
        "llm": settings.ollama_model,
        "cloudFallback": bool(settings.cloud_url and settings.cloud_api_key),
        "threshold": settings.suggest_threshold,
    }


@router.post("/kernel/ingest", response_model=IngestResponse)
async def ingest(req: IngestRequest) -> IngestResponse:
    """事件入图：抽取 → 落实体 → 落事件 → 建关系。"""
    conn = get_connection()
    ts = now()

    result = await extract(req.rawText)
    event_id = repo.upsert_event(
        conn, req.eventId, req.type, req.source, req.rawText, req.occurredAt or ts, ts
    )

    entity_outs: list[EntityOut] = []
    for ent in result.entities:
        eid = repo.upsert_entity(conn, ent["type"], ent["name"], ts)
        entity_outs.append(EntityOut(id=eid, type=ent["type"], name=ent["name"]))
        if ent["type"] == "Person":
            repo.link_person_to_event(conn, eid, event_id, ts)
        elif ent["type"] == "Topic":
            repo.link_event_to_topic(conn, event_id, eid, ts)

    rel_outs = [
        RelationOut(
            from_id=stable_id(r.get("source_type", ""), r.get("source", "")),
            to_id=stable_id(r.get("target_type", ""), r.get("target", "")),
            type=r["type"],
        )
        for r in result.relations
    ]

    return IngestResponse(
        eventId=event_id,
        entities=entity_outs,
        relations=rel_outs,
        confidence=0.8 if entity_outs else 0.0,
        llmUsed=result.llm_used,
    )


@router.post("/kernel/retrieve", response_model=RetrieveResponse)
def retrieve(req: RetrieveRequest) -> RetrieveResponse:
    """P0 为关键词匹配；P1 换成 embedding 向量检索 + 图谱扩散。"""
    conn = get_connection()
    rows = repo.search_events_by_text(conn, req.query, req.topK)
    return RetrieveResponse(
        results=[
            RetrieveResult(
                nodeId=r[0],
                type=r[1] or "Event",
                score=1.0,  # P0 无排序分数，P1 用向量相似度替换
                snippet=(r[2] or "")[:200],
            )
            for r in rows
        ]
    )


@router.post("/kernel/suggest", response_model=SuggestResponse)
def suggest() -> SuggestResponse:
    """主动建议：扫描周期性习惯，产出候选。已静默的 pattern 不返回。"""
    conn = get_connection()
    items = build_suggestions(conn, now())
    if not items:
        return SuggestResponse(suggestions=[], coldStart=True)
    return SuggestResponse(
        suggestions=[Suggestion(**it) for it in items], coldStart=False
    )


@router.post("/kernel/feedback", response_model=FeedbackResponse)
def feedback(req: FeedbackRequest) -> FeedbackResponse:
    """反馈闭环。没有这条回路，主动式助手会被用户关掉通知。"""
    conn = get_connection()
    res = feedback_engine.apply_feedback(conn, req.habitId, req.action, req.at or now())
    if res is None:
        raise HTTPException(status_code=404, detail=f"未找到习惯: {req.habitId}")
    new_confidence, muted = res
    return FeedbackResponse(ok=True, patternMuted=muted, newWeight=round(new_confidence, 3))


@router.get("/kernel/graph/subgraph")
def subgraph(center: str | None = None, depth: int = 2) -> dict:
    """供前端做图谱可视化。

    P0 只返回最近事件及其关联主题，depth 仅用于换算条数上限；
    P1 应改成以 center 为起点的真正 BFS 展开。
    """
    conn = get_connection()
    limit = max(10, min(200, depth * 50))

    nodes: list[dict] = []
    for eid, etype, text, source, at in repo.list_recent_events(conn, limit):
        nodes.append(
            {
                "id": eid,
                "type": etype or "Event",
                "label": (text or "")[:60],
                "source": source,
                "at": at.isoformat() if at else None,
            }
        )

    edges: list[dict] = []
    seen: set[str] = set()
    for src, dst, name in repo.list_event_topic_edges(conn):
        key = f"{src}->{dst}"
        if key in seen:
            continue
        seen.add(key)
        edges.append({"source": src, "target": dst, "type": "RELATES_TO"})
        if not any(n["id"] == dst for n in nodes):
            nodes.append({"id": dst, "type": "Topic", "label": name, "source": None, "at": None})

    return {"nodes": nodes, "edges": edges}
