"""Kùzu 连接与 schema 初始化。全局单例连接（Kùzu 单写者，不要并发多连接写）。"""

from __future__ import annotations

import threading

import kuzu

from config import BASE_DIR, settings

_lock = threading.Lock()
_conn: kuzu.Connection | None = None


def _init_schema(conn: kuzu.Connection) -> None:
    """
    schema.cypher 里所有 DDL 都带 IF NOT EXISTS，可安全重复执行。

    注意顺序：必须**先剔除整行注释，再按分号切分**。
    反过来的话，文件开头的注释会和第一条 CREATE 语句粘成同一块，
    被误判为纯注释整个跳过 —— Person 表建不出来，后续建边就会报
    "Table Person does not exist"。
    """
    ddl = (BASE_DIR / "schema.cypher").read_text(encoding="utf-8")
    body = "\n".join(
        line for line in ddl.splitlines() if not line.lstrip().startswith("--")
    )
    for stmt in (s.strip() for s in body.split(";") if s.strip()):
        conn.execute(stmt)


def get_connection() -> kuzu.Connection:
    global _conn
    if _conn is None:
        with _lock:
            if _conn is None:
                settings.data_dir.mkdir(parents=True, exist_ok=True)
                db = kuzu.Database(str(settings.data_dir / "memu.db"))
                conn = kuzu.Connection(db)
                _init_schema(conn)
                _conn = conn
    return _conn
