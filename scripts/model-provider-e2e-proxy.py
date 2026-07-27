#!/usr/bin/env python3
"""Prompt-free provider boundary recorder for the Model Context Envelope E2E."""

from __future__ import annotations

import json
import os
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


UPSTREAM_BASE_URL = os.environ["UPSTREAM_BASE_URL"].rstrip("/")
CONTROL_TOKEN = os.environ["CONTROL_TOKEN"]
PUBLIC_PREFIX = os.environ.get("PUBLIC_PREFIX", "/v1").rstrip("/")
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "8080"))
UPSTREAM_MAX_ATTEMPTS = int(os.environ.get("UPSTREAM_MAX_ATTEMPTS", "3"))


class RecorderState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.total_requests = 0
        self.marker_requests = 0
        self.count_marker = b""
        self.controlled_trigger = b""
        self.reasoning_marker = ""
        self.response_content = "CONTROLLED_REASONING_OK"
        self.fail_after_reasoning = False
        self.controlled_tool_id = ""
        self.controlled_tool_arguments: dict[str, object] = {}
        self.controlled_tool_pending = False
        self.controlled_tool_calls = 0

    def configure(self, payload: dict[str, object]) -> None:
        with self.lock:
            self.total_requests = 0
            self.marker_requests = 0
            self.count_marker = str(payload.get("countMarker", "")).encode()
            self.controlled_trigger = str(payload.get("controlledTrigger", "")).encode()
            self.reasoning_marker = str(payload.get("reasoningMarker", ""))
            self.response_content = str(payload.get("responseContent", "CONTROLLED_REASONING_OK"))
            self.fail_after_reasoning = bool(payload.get("failAfterReasoning", False))
            self.controlled_tool_id = str(payload.get("controlledToolId", ""))
            arguments = payload.get("controlledToolArguments", {})
            if not isinstance(arguments, dict):
                raise ValueError("controlledToolArguments must be an object")
            self.controlled_tool_arguments = arguments
            self.controlled_tool_pending = bool(self.controlled_tool_id)
            self.controlled_tool_calls = 0

    def observe(self, body: bytes) -> tuple[bool, str, str, bool, str, dict[str, object]]:
        with self.lock:
            self.total_requests += 1
            if self.count_marker and self.count_marker in body:
                self.marker_requests += 1
            controlled = bool(self.controlled_trigger and self.controlled_trigger in body)
            tool_id = ""
            tool_arguments: dict[str, object] = {}
            reasoning_marker = self.reasoning_marker
            if controlled and self.controlled_tool_pending:
                self.controlled_tool_pending = False
                self.controlled_tool_calls += 1
                tool_id = self.controlled_tool_id
                tool_arguments = self.controlled_tool_arguments
            elif controlled and self.controlled_tool_id:
                reasoning_marker = ""
            return (
                controlled,
                reasoning_marker,
                self.response_content,
                self.fail_after_reasoning,
                tool_id,
                tool_arguments,
            )

    def snapshot(self) -> dict[str, int]:
        with self.lock:
            return {
                "totalRequests": self.total_requests,
                "markerRequests": self.marker_requests,
                "controlledToolCalls": self.controlled_tool_calls,
            }


STATE = RecorderState()


class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:
        if self.path == "/healthz":
            self._send_json(200, {"status": "UP"})
            return
        if self.path == "/__e2e/stats" and self._authorized():
            self._send_json(200, STATE.snapshot())
            return
        self._send_json(404, {"error": "not_found"})

    def do_POST(self) -> None:
        if self.path == "/__e2e/config":
            if not self._authorized():
                self._send_json(401, {"error": "unauthorized"})
                return
            payload = self._read_json()
            STATE.configure(payload)
            self._send_json(200, {"configured": True})
            return

        body = self._read_body()
        (
            controlled,
            reasoning_marker,
            response_content,
            fail_after_reasoning,
            tool_id,
            tool_arguments,
        ) = STATE.observe(body)
        if controlled:
            self._send_controlled_stream(
                reasoning_marker,
                response_content,
                fail_after_reasoning,
                tool_id,
                tool_arguments,
            )
            return
        self._forward(body)

    def _forward(self, body: bytes) -> None:
        relative_path = self.path
        if PUBLIC_PREFIX and relative_path.startswith(PUBLIC_PREFIX + "/"):
            relative_path = relative_path[len(PUBLIC_PREFIX):]
        upstream_url = UPSTREAM_BASE_URL + "/" + relative_path.lstrip("/")
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in {"host", "content-length", "connection", "accept-encoding"}
        }
        headers["Accept-Encoding"] = "identity"
        for attempt in range(1, UPSTREAM_MAX_ATTEMPTS + 1):
            request = urllib.request.Request(upstream_url, data=body, headers=headers, method="POST")
            try:
                with urllib.request.urlopen(request, timeout=300) as response:
                    payload = response.read()
                    self._send_bytes(response.status, response.headers.get_content_type(), payload)
                    return
            except urllib.error.HTTPError as error:
                self._send_bytes(error.code, error.headers.get_content_type(), error.read())
                return
            except urllib.error.URLError as error:
                if attempt == UPSTREAM_MAX_ATTEMPTS:
                    self._send_json(502, {"error": "upstream_unavailable", "reason": str(error.reason)})
                    return
                time.sleep(0.5 * attempt)

    def _send_controlled_stream(
        self,
        reasoning_marker: str,
        response_content: str,
        fail_after_reasoning: bool,
        tool_id: str,
        tool_arguments: dict[str, object],
    ) -> None:
        chunks: list[dict[str, object]] = [
            {"id": "e2e-controlled", "object": "chat.completion.chunk", "choices": [
                {"index": 0, "delta": {"role": "assistant", "reasoning_content": reasoning_marker}}
            ]}
        ]
        if tool_id:
            chunks.extend([
                {"id": "e2e-controlled", "object": "chat.completion.chunk", "choices": [
                    {"index": 0, "delta": {"tool_calls": [{
                        "index": 0,
                        "id": "e2e-controlled-tool-call",
                        "type": "function",
                        "function": {
                            "name": tool_id,
                            "arguments": json.dumps(tool_arguments, separators=(",", ":")),
                        },
                    }]}}
                ]},
                {"id": "e2e-controlled", "object": "chat.completion.chunk", "choices": [
                    {"index": 0, "delta": {}, "finish_reason": "tool_calls"}
                ], "usage": {"prompt_tokens": 64, "completion_tokens": 8, "total_tokens": 72}},
            ])
        else:
            chunks.extend([
                {"id": "e2e-controlled", "object": "chat.completion.chunk", "choices": [
                    {"index": 0, "delta": {"content": response_content}}
                ]},
                {"id": "e2e-controlled", "object": "chat.completion.chunk", "choices": [
                    {"index": 0, "delta": {}, "finish_reason": "stop"}
                ], "usage": {"prompt_tokens": 64, "completion_tokens": 8, "total_tokens": 72}},
            ])
        if fail_after_reasoning:
            chunks = chunks[:1]
        payload = "".join(f"data: {json.dumps(chunk, separators=(',', ':'))}\n\n" for chunk in chunks)
        if not fail_after_reasoning:
            payload += "data: [DONE]\n\n"
        self._send_bytes(200, "text/event-stream", payload.encode())

    def _authorized(self) -> bool:
        return self.headers.get("Authorization", "") == f"Bearer {CONTROL_TOKEN}"

    def _read_json(self) -> dict[str, object]:
        body = self._read_body()
        value = json.loads(body or b"{}")
        if not isinstance(value, dict):
            raise ValueError("JSON body must be an object")
        return value

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def _send_json(self, status: int, value: dict[str, object]) -> None:
        self._send_bytes(status, "application/json", json.dumps(value, separators=(",", ":")).encode())

    def _send_bytes(self, status: int, content_type: str, payload: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(payload)
        self.close_connection = True

    def log_message(self, _format: str, *args: object) -> None:
        return


def main() -> None:
    upstream = urlsplit(UPSTREAM_BASE_URL)
    if upstream.scheme not in {"http", "https"} or not upstream.netloc:
        raise ValueError("UPSTREAM_BASE_URL must be an absolute HTTP(S) URL")
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), ProxyHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
