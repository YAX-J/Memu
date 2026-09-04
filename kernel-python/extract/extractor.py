"""
抽取 pipeline：缓存 → 本地模型 → 云端兜底。
上层只调 extract()，不关心走了哪条路。
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field

from .llm import EXTRACT_PROMPT, call_cloud, call_ollama, parse_extraction

MAX_TEXT = 4000

# P0 用进程内字典。P1 应换成持久化的 embedding 相似度缓存（见 DESIGN.md 成本优化第 2 条）
_cache: dict[str, tuple[list[dict], list[dict]]] = {}


@dataclass
class ExtractionResult:
    entities: list[dict] = field(default_factory=list)
    relations: list[dict] = field(default_factory=list)
    llm_used: str = "local"  # local | cloud | cache | failed


async def extract(text: str) -> ExtractionResult:
    key = hashlib.sha1(text.strip().encode("utf-8")).hexdigest()
    if key in _cache:
        entities, relations = _cache[key]
        return ExtractionResult(entities, relations, "cache")

    prompt = EXTRACT_PROMPT.format(text=text[:MAX_TEXT])

    for name, caller in (("local", call_ollama), ("cloud", call_cloud)):
        try:
            raw = await caller(prompt)
            entities, relations = parse_extraction(raw)
            if entities:
                _cache[key] = (entities, relations)
                return ExtractionResult(entities, relations, name)
        except Exception:
            # 本地不可用就试云端，云端不可用就放弃，不向上抛
            continue

    return ExtractionResult([], [], "failed")
