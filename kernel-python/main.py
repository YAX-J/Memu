"""
Memu AI 内核入口。

这个文件只做应用装配，不放任何业务逻辑：
  路由  → api/routes.py
  契约  → api/schemas.py
  抽取  → extract/
  引擎  → engine/
  图谱  → graph/

启动：
    uvicorn main:app --host 127.0.0.1 --port 8765 --reload
"""

from __future__ import annotations

from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI

from api.routes import router
from config import settings
from graph.db import get_connection


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时即建库建表，避免第一个请求承担初始化延迟
    get_connection()
    yield


def create_app() -> FastAPI:
    app = FastAPI(title="Memu AI Kernel", version="0.2.0", lifespan=lifespan)
    app.include_router(router)
    return app


app = create_app()


if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
