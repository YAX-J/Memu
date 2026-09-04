"""
LLM 调度：本地 Ollama 优先，失败或不达标才回落云端。
这是"运行成本更低"的落点——高频抽取全走本地，云端只兜底。
"""

from __future__ import annotations

import json
import re

import httpx

from config import settings
from domain import ENTITY_TYPES, RELATION_TYPES

EXTRACT_PROMPT = """从下面内容中抽取实体和关系，只输出 JSON，不要任何解释或代码块标记。

实体类型只能从这些里选：Person（人）、Project（项目）、Task（任务）、Topic（主题）
关系类型只能从这些里选：PARTICIPATES_IN（人参与事件）、RELATES_TO（事件关联主题）、BELONGS_TO（任务归属项目）

输出格式：
{{"entities":[{{"type":"Person","name":"张三"}}],
  "relations":[{{"source_type":"Person","source":"张三","target_type":"Topic","target":"版本评审","type":"PARTICIPATES_IN"}}]}}

如果内容里没有可抽取的实体，返回 {{"entities":[],"relations":[]}}。

内容：
{text}"""


class LLMUnavailable(RuntimeError):
    """本地与云端都不可用时抛出。"""


async def call_ollama(prompt: str) -> str:
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(
            f"{settings.ollama_url}/api/generate",
            json={
                "model": settings.ollama_model,
                "prompt": prompt,
                "stream": False,
                "format": "json",
            },
        )
        resp.raise_for_status()
        return resp.json()["response"]


async def call_cloud(prompt: str) -> str:
    if not settings.cloud_url or not settings.cloud_api_key:
        raise LLMUnavailable("云端兜底未配置（MEMU_CLOUD_URL / MEMU_CLOUD_API_KEY 为空）")
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(
            settings.cloud_url,
            headers={"Authorization": f"Bearer {settings.cloud_api_key}"},
            json={
                "model": settings.cloud_model,
                "input": {"messages": [{"role": "user", "content": prompt}]},
            },
        )
        resp.raise_for_status()
        data = resp.json()
        return data["output"]["choices"][0]["message"]["content"]


def parse_extraction(raw: str) -> tuple[list[dict], list[dict]]:
    """LLM 常吐代码块标记或前后缀废话，这里做容错解析。"""
    cleaned = re.sub(r"^```(?:json)?|```$", "", raw.strip(), flags=re.MULTILINE).strip()
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start == -1 or end == -1:
        return [], []
    try:
        data = json.loads(cleaned[start : end + 1])
    except json.JSONDecodeError:
        return [], []

    entities = [
        e
        for e in data.get("entities", [])
        if isinstance(e, dict) and e.get("type") in ENTITY_TYPES and e.get("name")
    ]
    relations = [
        r
        for r in data.get("relations", [])
        if isinstance(r, dict) and r.get("type") in RELATION_TYPES
    ]
    return entities, relations
