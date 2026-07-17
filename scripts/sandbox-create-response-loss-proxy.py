#!/usr/bin/env python3

import argparse
import http.client
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


CREATE_PATH = "/internal/sandbox/runtime/sessions"
OWNERSHIP_PATH = "/internal/sandbox/runtime/session-ownership"
CLOSE_PATH = "/internal/sandbox/runtime/close"


def emit(event, **fields):
    print(json.dumps({"event": event, **fields}, separators=(",", ":")), flush=True)


class ProxyState:
    def __init__(self, upstream_host, upstream_port, release_file):
        self.upstream_host = upstream_host
        self.upstream_port = upstream_port
        self.release_file = release_file
        self.lock = threading.Lock()
        self.create_request_count = 0

    def register_create_request(self):
        with self.lock:
            self.create_request_count += 1
            return self.create_request_count, self.create_request_count == 1


class ResponseLossProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        self._forward()

    def do_POST(self):
        self._forward()

    def log_message(self, _format, *_args):
        return

    def _forward(self):
        content_length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(content_length) if content_length else b""
        drop_create_response = False
        if self.path == CREATE_PATH:
            request_number, drop_create_response = self.server.state.register_create_request()
            emit("create-request-received", path=self.path, requestNumber=request_number)
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in {"host", "connection", "content-length"}
        }
        headers["Host"] = f"{self.server.state.upstream_host}:{self.server.state.upstream_port}"
        if body:
            headers["Content-Length"] = str(len(body))

        connection = http.client.HTTPConnection(
            self.server.state.upstream_host,
            self.server.state.upstream_port,
            timeout=65,
        )
        try:
            connection.request(self.command, self.path, body=body, headers=headers)
            response = connection.getresponse()
            response_body = response.read()
            response_headers = response.getheaders()
        except Exception as exc:
            emit("upstream-error", path=self.path, error=type(exc).__name__)
            self.send_error(502)
            return
        finally:
            connection.close()

        if self.path == CREATE_PATH and drop_create_response:
            session_id = self._json_field(response_body, "sessionId")
            emit("create-response-dropped", path=self.path, sessionId=session_id, status=response.status)
            self.close_connection = True
            return

        if self.path == OWNERSHIP_PATH:
            emit(
                "ownership-response-forwarded",
                path=self.path,
                sessionId=self._json_field(response_body, "sessionId"),
                ownership=self._json_field(response_body, "ownership"),
                status=response.status,
            )

        if self.path == CLOSE_PATH:
            emit(
                "close-response-held",
                path=self.path,
                sessionId=self._json_field(response_body, "sessionId"),
                status=response.status,
            )
            deadline = time.monotonic() + 60
            while not os.path.exists(self.server.state.release_file):
                if time.monotonic() >= deadline:
                    emit("close-response-gate-timeout", path=self.path)
                    self.close_connection = True
                    return
                time.sleep(0.05)
            emit("close-response-released", path=self.path)

        self.send_response(response.status)
        for key, value in response_headers:
            if key.lower() not in {"connection", "content-length", "transfer-encoding"}:
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(response_body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(response_body)
        self.close_connection = True

    @staticmethod
    def _json_field(body, field):
        try:
            value = json.loads(body.decode("utf-8")).get(field)
            return "" if value is None else str(value)
        except (UnicodeDecodeError, json.JSONDecodeError, AttributeError):
            return ""


class ResponseLossProxyServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, state):
        super().__init__(address, ResponseLossProxyHandler)
        self.state = state


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen-port", type=int, default=9090)
    parser.add_argument("--upstream-host", required=True)
    parser.add_argument("--upstream-port", type=int, default=9090)
    parser.add_argument("--release-file", default="/tmp/release-close-response")
    args = parser.parse_args()

    state = ProxyState(args.upstream_host, args.upstream_port, args.release_file)
    server = ResponseLossProxyServer(("0.0.0.0", args.listen_port), state)
    emit("proxy-ready", listenPort=args.listen_port, upstreamHost=args.upstream_host)
    server.serve_forever()


if __name__ == "__main__":
    main()
