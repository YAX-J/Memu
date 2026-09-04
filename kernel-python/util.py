"""通用工具。"""

from __future__ import annotations

import hashlib
import unicodedata
from datetime import datetime, timezone


def _normalize(s: str) -> str:
    """实体名归一化：全角→半角（NFKC）+ 去空白 + 小写。

    让「张 伟」「张伟」「张　伟」「ZHANG WEI」等写法映射到同一节点，
    这是实体消歧里最廉价、可离线做的那一层（别名/指代消解留给 LLM）。
    """
    s = unicodedata.normalize("NFKC", s)
    return "".join(s.split()).lower()


def stable_id(*parts: str) -> str:
    """
    由内容生成稳定 ID，保证同名实体幂等落图。
    大小写、全/半角、首尾及内部空白均不敏感，避免「张三」/「张三 」产生两个节点。
    """
    raw = "|".join(_normalize(p) for p in parts if p.strip())
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
