"""
本地 mock 云端点（验证工具，不参与运行时）。

用途：模拟 DashScope 云端抽取接口，验证「本地 Ollama 挂了 → 自动回落云端」
这条兜底链路真的通。返回固定抽取结果（Topic「云端兜底验证」），便于断言。

启动：
    python mock_cloud.py          # 监听 127.0.0.1:9999
配合：
    MEMU_CLOUD_URL=http://127.0.0.1:9999 MEMU_CLOUD_API_KEY=test-key 启动内核
"""

from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 9999


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length))
        prompt = body.get("input", {}).get("messages", [{}])[0].get("content", "")
        print(f"[mock-cloud] 收到请求，prompt 末 60 字: ...{prompt[-60:]}", flush=True)

        content = json.dumps(
            {
                "entities": [
                    {"type": "Topic", "name": "云端兜底验证"},
                    {"type": "Person", "name": "云端测试"},
                ],
                "relations": [],
            },
            ensure_ascii=False,
        )
        resp = {"output": {"choices": [{"message": {"content": content}}]}}
        data = json.dumps(resp, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"[mock-cloud] listening on 127.0.0.1:{PORT}", flush=True)
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
