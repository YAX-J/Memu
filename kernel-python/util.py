"""通用工具。"""

from __future__ import annotations

import hashlib
from datetime import datetime, timezone


def stable_id(*parts: str) -> str:
    """
    由内容生成稳定 ID，保证同名实体幂等落图。
    大小写与首尾空格不敏感，避免"张三"/"张三 "产生两个节点。
    """
    raw = "|".join(p.strip().lower() for p in parts if p and p.strip())
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]


def now() -> datetime:
    """统一时间入口：始终返回带时区的时间。"""
    return datetime.now(timezone.utc)


def ensure_aware(dt: datetime | None) -> datetime | None:
    """
    Kùzu 取回的 TIMESTAMP 可能是 naive，与 aware 时间做差会抛 TypeError。
    所有从库里读出的时间都先过这个函数。
    """
    if dt is None:
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)
