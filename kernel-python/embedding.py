"""
文本嵌入：零依赖字符 n-gram 哈希 + 持久化缓存。

设计意图（对应 docs/DESIGN.md 第 6 节成本优化 #2「Embedding 相似度缓存」）：
- 后端优先级：sentence-transformers 本地语义模型（若已安装且模型可加载）
  → 字符 n-gram 哈希（numpy 实现，零下载、零外部依赖、确定性）。
- embedding 只对首次见到的文本算一次，之后走磁盘缓存，进程重启不丢。
- 相似文本共享 n-gram，余弦相似度可用于两处：
    1. 抽取阶段模糊缓存命中 → 跳过 LLM（近似输入零推理）
    2. retrieve 向量检索 → 关键词匹配升级为语义召回

为什么默认 n-gram 而不是逼着先下模型：模型动辄 400MB+，且本机 Ollama
可能不在线；n-gram 对中文短文本相似度已够用，机制先跑通，模型后续可平滑升级。
"""

from __future__ import annotations

import hashlib
import json
import threading
from pathlib import Path

import numpy as np

from config import settings

# 向量维度：中文短文本检索 384 足够，numpy 点积开销可忽略
DIM = 384
# (n, 权重)：unigram 兜单字查询，bigram/trigram 兜词级匹配
_NGRAM_WEIGHTS = ((1, 0.3), (2, 0.7), (3, 1.0))

_CACHE_PATH: Path = settings.data_dir / "embedding_cache.json"

_cache: dict[str, list[float]] = {}
_lock = threading.Lock()


def _hash_slot(token: str) -> int:
    """md5 转 int 取模，把 n-gram 稳定散到固定维度。"""
    return int(hashlib.md5(token.encode("utf-8")).hexdigest(), 16) % DIM


def _hashing_vector(text: str) -> np.ndarray:
    """字符 n-gram 哈希成稀疏向量，L2 归一化。"""
    vec = np.zeros(DIM, dtype=np.float32)
    for n, w in _NGRAM_WEIGHTS:
        for i in range(len(text) - n + 1):
            vec[_hash_slot(text[i : i + n])] += w
    norm = float(np.linalg.norm(vec))
    if norm > 0:
        vec /= norm
    return vec


def _load_cache() -> None:
    global _cache
    if _CACHE_PATH.exists():
        try:
            _cache = json.loads(_CACHE_PATH.read_text(encoding="utf-8"))
        except Exception:
            # 缓存损坏不影响主流程，从空开始
            _cache = {}


def _save_cache() -> None:
    _CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = _CACHE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(_cache, ensure_ascii=False), encoding="utf-8")
    tmp.replace(_CACHE_PATH)


def _try_sentence_model():
    """若已安装 sentence-transformers 且模型可加载，返回 encode 函数，否则 None。

    这是可选升级：requirements.txt 里 sentence-transformers 默认注释。
    装好后把 MEMU_EMBEDDING_MODEL 设为模型名即可自动切换到语义向量。
    """
    try:
        from sentence_transformers import SentenceTransformer  # type: ignore

        model = SentenceTransformer(settings.embedding_model)
        return model.encode
    except Exception:
        return None


# 启动即加载磁盘缓存
_load_cache()
# 尝试语义模型（未装则 None，回落 n-gram）
_sentence_encode = _try_sentence_model()


def embed(text: str) -> np.ndarray:
    """文本 → 归一化向量，带磁盘缓存。返回 float32 ndarray。"""
    key = (text or "").strip()
    with _lock:
        if key in _cache:
            return np.asarray(_cache[key], dtype=np.float32)

    if _sentence_encode is not None:
        try:
            vec = np.asarray(_sentence_encode(key), dtype=np.float32).reshape(-1)
        except Exception:
            vec = _hashing_vector(key)
    else:
        vec = _hashing_vector(key)

    norm = float(np.linalg.norm(vec))
    if norm > 0:
        vec = vec / norm

    with _lock:
        _cache[key] = vec.tolist()
        _save_cache()
    return vec


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    """余弦相似度。输入已归一化时等价于点积。"""
    denom = float(np.linalg.norm(a) * np.linalg.norm(b))
    if denom == 0:
        return 0.0
    return float(np.dot(a, b) / denom)
