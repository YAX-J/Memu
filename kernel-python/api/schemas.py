"""
Java ↔ Python 的契约模型。

改这个文件等于改契约，必须同步三处：
  1. docs/DESIGN.md 第 4 节
  2. backend-java 的 kernel/dto
  3. 前端调用方
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field

LLMUsed = Literal["local", "cloud", "cache", "rules", "failed"]
FeedbackAction = Literal["accepted", "snoozed", "dismissed"]


# ---------------------------------------------------------------- ingest


class IngestRequest(BaseModel):
    eventId: str
    source: str = "unknown"
    type: str = "note"
    occurredAt: datetime | None = None
    rawText: str
    # Java 侧 WebClient 默认会发 null，可选字段必须容忍 null（否则 422）
    meta: dict[str, Any] | None = Field(default=None)


class EntityOut(BaseModel):
    id: str
    type: str
    name: str


class RelationOut(BaseModel):
    """线上 JSON 用 from/to；字段名避开 Python 关键字，靠 alias 对齐。"""

    model_config = {"populate_by_name": True}

    from_id: str = Field(..., serialization_alias="from")
    to_id: str = Field(..., serialization_alias="to")
    type: str


class IngestResponse(BaseModel):
    eventId: str
    entities: list[EntityOut] = Field(default_factory=list)
    relations: list[RelationOut] = Field(default_factory=list)
    confidence: float = 0.0
    llmUsed: LLMUsed = "local"


# ---------------------------------------------------------------- retrieve


class RetrieveRequest(BaseModel):
    query: str
    topK: int = 10
    timeRange: list[str] | None = None


class RetrieveResult(BaseModel):
    nodeId: str
    type: str
    score: float
    snippet: str = ""


class RetrieveResponse(BaseModel):
    results: list[RetrieveResult] = Field(default_factory=list)


# ---------------------------------------------------------------- suggest


class SuggestAction(BaseModel):
    type: str
    payload: dict[str, Any] = Field(default_factory=dict)


class Suggestion(BaseModel):
    """id 即 habitId —— Java 侧据此回传反馈。"""

    id: str
    title: str
    reason: str
    confidence: float
    actions: list[SuggestAction] = Field(default_factory=list)


class SuggestResponse(BaseModel):
    suggestions: list[Suggestion] = Field(default_factory=list)
    coldStart: bool = False


# ---------------------------------------------------------------- feedback


class FeedbackRequest(BaseModel):
    """
    habitId 即 Suggestion.id。
    建议实例与用户动作由 Java 侧保存，Python 只负责调整习惯权重。
    """

    habitId: str
    action: FeedbackAction
    at: datetime | None = None


class FeedbackResponse(BaseModel):
    ok: bool = True
    patternMuted: bool = False
    newWeight: float = 0.0
