"""
抽取 pipeline：缓存 → 本地模型 → 云端兜底。
上层只调 extract()，不关心走了哪条路。

缓存策略（对应 docs/DESIGN.md 第 6 节成本优化 #2）：
- 精确 hash 缓存，**持久化到磁盘**（data/extract_cache.json），跨进程重启仍复用，
  重复/完全相同输入零推理——这是「跳过 LLM」的主要来源。
- 模糊相似度缓存**暂缓**：字符 n-gram 哈希区分不了「换人名」和「差虚词」
  （实测「王芳 参加晨会」vs「李雷 参加晨会」≈0.64，与差一个「了」的 0.66 几乎无差），
  误复用会把实体抽错。要安全地做近似复用需语义 embedding（sentence-transformers），
  待模型接入后再开。
"""

from __future__ import annotations

import hashlib
import json
import threading
from dataclasses import dataclass, field
from pathlib import Path

from config import settings

from .llm import EXTRACT_PROMPT, call_cloud, call_ollama, parse_extraction
from .rules import extract_rules

MAX_TEXT = 4000

_CACHE_PATH: Path = settings.data_dir / "extract_cache.json"

# 文本 hash → {"entities": [...], "relations": [...]}
_cache: dict[str, dict] = {}
_lock = threading.Lock()


def _load_cache() -> None:
    global _cache
    if _CACHE_PATH.exists():
        try:
            _cache = json.loads(_CACHE_PATH.read_text(encoding="utf-8"))
        except Exception:
            _cache = {}


def _save_cache() -> None:
    _CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = _CACHE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(_cache, ensure_ascii=False), encoding="utf-8")
    tmp.replace(_CACHE_PATH)


_load_cache()


@dataclass
class ExtractionResult:
    entities: list[dict] = field(default_factory=list)
    relations: list[dict] = field(default_factory=list)
    llm_used: str = "local"  # local | cloud | cache | rules | failed


def _hit(key: str) -> tuple[list[dict], list[dict]] | None:
    with _lock:
        entry = _cache.get(key)
    if entry is None:
        return None
    return entry.get("entities", []), entry.get("relations", [])


def _store(key: str, entities: list[dict], relations: list[dict]) -> None:
    with _lock:
        _cache[key] = {"entities": entities, "relations": relations}
        _save_cache()


async def extract(text: str) -> ExtractionResult:
    key = hashlib.sha1(text.strip().encode("utf-8")).hexdigest()
    hit = _hit(key)
    if hit is not None:
        entities, relations = hit
        return ExtractionResult(entities, relations, "cache")

    prompt = EXTRACT_PROMPT.format(text=text[:MAX_TEXT])

    # 本地 Ollama → 云端 → 离线规则。规则是零成本兜底，只在 LLM 全不可用时启用，
    # 不抢 LLM 的活（LLM 能抽 Project/Task 等规则覆盖不到的实体）。
    for name, caller in (("local", call_ollama), ("cloud", call_cloud)):
        try:
            raw = await caller(prompt)
            entities, relations = parse_extraction(raw)
            if entities:
                _store(key, entities, relations)
                return ExtractionResult(entities, relations, name)
        except Exception:
            # 本地不可用就试云端，云端不可用就降级，不向上抛
            continue

    entities, relations = extract_rules(text)
    if entities:
        _store(key, entities, relations)
        return ExtractionResult(entities, relations, "rules")

    return ExtractionResult([], [], "failed")
