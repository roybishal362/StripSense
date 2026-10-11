"""The family server's HTTP side. Standard library only, so it runs on any laptop with Python and no installs.

    GET  /healthz                    is the server up
    GET  /                           the family page
    POST /api/weeks                  body: the exported week.csv as text. Needs the token when one is set.
    GET  /api/people                 everyone a week has been received for
    GET  /api/people/<id>/week       the last seven days on record (?end=YYYY-MM-DD for an earlier week)
    GET  /api/people/<id>/alerts     doses that need a look
    GET  /api/people/<id>/summary    counts
    GET  /api/people/<id>/phone      what the person's phone last said about itself (battery, charging, how long ago)
    POST /api/link                   a sealed message from a phone with the family link on. No token: only a phone
                                     holding the pairing code can make a message that opens.

When a token is set, every /api/ read needs it too, so a page opened from another machine must carry it:
http://<address>:8765/?token=<token>
"""
from __future__ import annotations

import json
import re
from datetime import datetime
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from . import link, service, weekfile

WEB = Path(__file__).parent / "web"
_PERSON = re.compile(r"^/api/people/(\d+)/(week|alerts|summary|phone)$")
_DAY = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_BOX = re.compile(r"^/api/box/([0-9a-f]{32})$")


def make_handler(db: sqlite3.Connection, token: str | None, link_key: bytes | None = None, pairing: str | None = None, relay_only: bool = False):
    class Handler(BaseHTTPRequestHandler):
        server_version = "StripSenseFamily/1"

        def log_message(self, fmt, *args):  # one short line per request, no bodies: a week is private
            print(f"{self.command} {urlparse(self.path).path} -> {args[1] if len(args) > 1 else ''}")

        def _send(self, status: int, body: bytes, kind: str = "application/json; charset=utf-8"):
            self.send_response(status)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(body)

        def _json(self, status: int, value):
            self._send(status, json.dumps(value, ensure_ascii=False).encode("utf-8"))

        def do_GET(self):
            url = urlparse(self.path)
            if url.path == "/healthz":
                return self._json(200, {"ok": True})
            if url.path in ("/", "/view"):
                # The family view: what a son or daughter opens on their own phone. It opens the sealed box itself.
                return self._send(200, (WEB / "view.html").read_bytes(), "text/html; charset=utf-8")
            if relay_only and not _BOX.match(url.path):
                # Open to the internet: only the family view and the sealed boxes exist here.
                return self._json(404, {"error": "no such page"})
            if url.path in ("/files", "/index.html"):
                return self._send(200, (WEB / "index.html").read_bytes(), "text/html; charset=utf-8")
            box = _BOX.match(url.path)
            if box:
                # No token: the name of a box cannot be guessed, and what is in it cannot be read without the family code.
                row = db.execute("SELECT sealed, received_at FROM box WHERE id = ?", (box.group(1),)).fetchone()
                if not row:
                    return self._json(404, {"error": "nothing here yet"})
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(len(row["sealed"])))
                self.send_header("Cache-Control", "no-store")
                self.send_header("X-Received-At", row["received_at"])
                self.end_headers()
                self.wfile.write(row["sealed"])
                return
            if url.path.startswith("/api/") and token and self.headers.get("X-StripSense-Token") != token:
                return self._json(401, {"error": "the token is missing or wrong"})
            if url.path == "/api/pairing":
                # The words a phone's camera reads off this page to connect. Null when the family link is off.
                return self._json(200, {"line": pairing})
            if url.path == "/api/people":
                return self._json(200, service.people(db))
            match = _PERSON.match(url.path)
            if not match:
                return self._json(404, {"error": "no such page"})
            person_id, what = int(match.group(1)), match.group(2)
            if what == "week":
                end = parse_qs(url.query).get("end", [None])[0]
                if end is not None and not _DAY.match(end):
                    return self._json(400, {"error": "end must be a date like 2026-10-10"})
                return self._json(200, service.week(db, person_id, end))
            if what == "alerts":
                return self._json(200, service.alerts(db, person_id))
            if what == "phone":
                return self._json(200, service.phone(db, person_id))
            return self._json(200, service.summary(db, person_id))

        def do_POST(self):
            box = _BOX.match(urlparse(self.path).path)
            if box:
                length = int(self.headers.get("Content-Length") or 0)
                if length < 28 or length > link.MAX_BYTES:
                    return self._json(400, {"error": "not a sealed message"})
                sealed = self.rfile.read(length)
                with db:
                    db.execute("INSERT INTO box (id, sealed, received_at) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET sealed = excluded.sealed, received_at = excluded.received_at",
                               (box.group(1), sealed, datetime.now().isoformat(timespec="seconds")))
                return self._json(201, {"kept": True})
            if relay_only:
                return self._json(404, {"error": "no such page"})
            if urlparse(self.path).path == "/api/link":
                length = int(self.headers.get("Content-Length") or 0)
                if link_key is None:
                    return self._json(404, {"error": "the family link is not turned on at this server"})
                if length <= 0 or length > link.MAX_BYTES:
                    return self._json(400, {"error": "not a link message"})
                try:
                    return self._json(201, service.link_in(db, link.open_message(self.rfile.read(length), link_key)))
                except (link.LinkError, weekfile.WeekFileError) as problem:
                    return self._json(401, {"error": str(problem)})
            if urlparse(self.path).path != "/api/weeks":
                return self._json(404, {"error": "no such page"})
            if token and self.headers.get("X-StripSense-Token") != token:
                return self._json(401, {"error": "the token is missing or wrong"})
            length = int(self.headers.get("Content-Length") or 0)
            if length <= 0 or length > weekfile.MAX_BYTES:
                return self._json(413 if length > 0 else 400, {"error": "send the week file as the body"})
            try:
                text = self.rfile.read(length).decode("utf-8")
                return self._json(201, service.ingest(db, text))
            except UnicodeDecodeError:
                return self._json(400, {"error": "the file is not text"})
            except weekfile.WeekFileError as problem:
                return self._json(400, {"error": str(problem)})

    return Handler


def serve(db: sqlite3.Connection, host: str = "127.0.0.1", port: int = 8765, token: str | None = None,
          link_code: str | None = None, address: str | None = None, relay_only: bool = False) -> ThreadingHTTPServer:
    """Starts listening and returns the server; the caller runs serve_forever(). Port 0 picks a free port, for tests.
    [address]: this computer's address on the home network, shown on the page for a phone to read."""
    server = ThreadingHTTPServer((host, port), None)
    line = f"STRIPSENSE {address}:{server.server_address[1]} {link_code}" if link_code and address else None
    server.RequestHandlerClass = make_handler(db, token, link.key(link_code) if link_code else None, line, relay_only)
    return server
