"""
集中配置。所有可调参数收口在这里，均可用环境变量覆盖。
改行为只动这个文件，不要把常量散落到各模块。
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent


def _s(key: str, default: str) -> str:
    return os.getenv(key, default)


def _f(key: str, default: float) -> float:
    return float(os.getenv(key, str(default)))


def _i(key: str, default: int) -> int:
    return int(os.getenv(key, str(default)))


@dataclass(frozen=True)
class Settings:
    # ---- 存储 ----
    data_dir: Path = Path(_s("MEMU_DATA_DIR", str(BASE_DIR / "data")))

    # ---- LLM：本地优先 + 云端兜底 ----
    ollama_url: str = _s("MEMU_OLLAMA_URL", "http://127.0.0.1:11434")
    ollama_model: str = _s("MEMU_OLLAMA_MODEL", "qwen2.5:7b")
    cloud_url: str | None = _s("MEMU_CLOUD_URL", "") or None
    cloud_api_key: str | None = _s("MEMU_CLOUD_API_KEY", "") or None
    cloud_model: str = _s("MEMU_CLOUD_MODEL", "qwen-plus")

    # ---- 主动引擎（公式见 docs/DESIGN.md 第 5 节）----
    suggest_threshold: float = _f("MEMU_SUGGEST_THRESHOLD", 0.60)
    dismiss_mute_count: int = _i("MEMU_DISMISS_MUTE_COUNT", 3)
    feedback_alpha: float = _f("MEMU_FEEDBACK_ALPHA", 0.20)
    cold_start_min_events: int = _i("MEMU_COLD_START_MIN_EVENTS", 3)
    recency_lambda: float = _f("MEMU_RECENCY_LAMBDA", 0.05)
    # 冷启动期没有反馈样本，feedbackScore 取中性值
    neutral_feedback_score: float = _f("MEMU_NEUTRAL_FEEDBACK_SCORE", 0.50)

    # ---- 服务 ----
    host: str = _s("MEMU_HOST", "127.0.0.1")
    port: int = _i("MEMU_PORT", 8765)


settings = Settings()
