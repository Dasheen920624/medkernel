#!/usr/bin/env python3
# 所属幕：P5 第二轮全新演练 · 幕9 系统接入（发布同步通道前置）。
# 用途：在演练服务器上模拟一个真实监听的院内第三方接收系统（如 HIS 网关），
#   供集成运维员在前台完成系统接入（健康探活）并承接映射包发布投递。
# 诚实性：这是演练用模拟外部系统，不属于 MedKernel 产品代码；它真实监听端口、
#   真实返回 HTTP 状态、真实把收到的投递载荷落盘留痕，不伪造任何产品行为。
# 契约（与 HttpIntegrationConnector 对齐）：
#   GET  /health   -> 200 {"status":"UP"}            健康探活
#   POST /messages -> 200 {"accepted":true,...}      接收投递并逐条落盘 JSONL
#   其他路径        -> 404
# 运行：python3 mock-third-party-receiver.py --host 127.0.0.1 --port 9301 \
#         --log-dir /zoesoft/medkernel/mock-third-party/received
import argparse
import hashlib
import json
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

write_lock = threading.Lock()


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class MockReceiverHandler(BaseHTTPRequestHandler):
    server_version = "P5MockThirdParty/1.0"
    log_dir: Path = Path(".")

    def _reply(self, status: int, body: dict) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self) -> None:  # noqa: N802 BaseHTTPRequestHandler 约定方法名
        if self.path == "/health":
            self._reply(200, {"status": "UP", "service": "p5-mock-third-party", "time": now_iso()})
            return
        self._reply(404, {"error": "NOT_FOUND", "path": self.path})

    def do_POST(self) -> None:  # noqa: N802 BaseHTTPRequestHandler 约定方法名
        if self.path != "/messages":
            self._reply(404, {"error": "NOT_FOUND", "path": self.path})
            return
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length > 0 else b""
        try:
            payload = json.loads(raw.decode("utf-8")) if raw else None
        except (UnicodeDecodeError, json.JSONDecodeError):
            payload = {"rawBase64Unparsed": True}
        record = {
            "receivedAt": now_iso(),
            "messageId": self.headers.get("X-MedKernel-Message-Id"),
            "traceId": self.headers.get("X-MedKernel-Trace-Id"),
            "eventType": self.headers.get("X-MedKernel-Event-Type"),
            "contentLength": length,
            "bodySha256": hashlib.sha256(raw).hexdigest(),
            "payload": payload,
        }
        target = self.log_dir / "messages.jsonl"
        with write_lock:
            with target.open("a", encoding="utf-8") as f:
                f.write(json.dumps(record, ensure_ascii=False) + "\n")
        self._reply(200, {
            "accepted": True,
            "messageId": record["messageId"],
            "bodySha256": record["bodySha256"],
        })

    def log_message(self, fmt: str, *args) -> None:
        # 访问日志走 stdout，交由 systemd journal 收集。
        print(f"{now_iso()} {self.address_string()} {fmt % args}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="P5 演练模拟第三方接收端")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9301)
    parser.add_argument("--log-dir", default="./received")
    args = parser.parse_args()

    log_dir = Path(args.log_dir)
    log_dir.mkdir(parents=True, exist_ok=True)
    MockReceiverHandler.log_dir = log_dir

    server = ThreadingHTTPServer((args.host, args.port), MockReceiverHandler)
    print(f"{now_iso()} 模拟第三方接收端启动 host={args.host} port={args.port} log_dir={log_dir}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
