#!/usr/bin/env python3
"""Temporary LAN-only receiver for Cable zoom diagnostics."""

import argparse
import ipaddress
import json
import os
import secrets
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", required=True, help="LAN IPv4 address of this PC")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    bind_address = ipaddress.ip_address(args.bind)
    if not bind_address.is_private or bind_address.is_loopback:
        parser.error("--bind must be a private LAN address")

    data_dir = Path(__file__).resolve().parents[1] / ".diagnostics"
    data_dir.mkdir(mode=0o700, exist_ok=True)
    os.chmod(data_dir, 0o700)
    token_path = data_dir / "token"
    if not token_path.exists():
        token_path.write_text(secrets.token_hex(24), encoding="ascii")
        os.chmod(token_path, 0o600)
    token = token_path.read_text(encoding="ascii").strip()
    output_path = data_dir / "events.jsonl"
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/health":
                self.send_error(404)
                return
            body = b"ok\n"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            try:
                peer = ipaddress.ip_address(self.client_address[0])
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.send_error(400)
                return
            if self.path != "/event" or not peer.is_private:
                self.send_error(404)
                return
            if not secrets.compare_digest(self.headers.get("X-Diagnostic-Token", ""), token):
                self.send_error(403)
                return
            if not 0 < length <= 65536:
                self.send_error(413)
                return
            try:
                event = json.loads(self.rfile.read(length))
                if not isinstance(event, dict):
                    raise ValueError("Expected object")
            except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
                self.send_error(400)
                return
            event["receivedAt"] = datetime.now(timezone.utc).isoformat()
            event["peer"] = str(peer)
            with lock:
                with output_path.open("a", encoding="utf-8") as output:
                    output.write(json.dumps(event, separators=(",", ":")) + "\n")
                os.chmod(output_path, 0o600)
            self.send_response(204)
            self.end_headers()

        def log_message(self, format_string, *args):
            if '" 204 ' not in format_string % args:
                print("%s %s" % (self.address_string(), format_string % args), flush=True)

    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    print(f"Listening on http://{args.bind}:{args.port}; logs in {output_path}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
