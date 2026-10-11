"""Command line for the family server.

    python -m stripsense_backend relay                 carry sealed family messages and serve the family view
    python -m stripsense_backend serve                 start the older file-based server on this laptop
    python -m stripsense_backend ingest week.csv       store one exported week file
    python -m stripsense_backend pull                  fetch the newest week file from the phone over USB and store it

"relay" is what the app uses now. With the family link turned on, the phone posts one sealed message to
/api/box/<id>; the relay keeps it and cannot open it, and the family view opens it in the browser with the
family code. The other three commands are the earlier way, which needs no network on the phone: the family
exports a week (Family, Files, Save the week) and it is carried over by the USB cable ("pull") or by picking
the file on the family page.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

import socket

from . import api, db as store, link, service, weekfile

DEFAULT_DB = Path(__file__).resolve().parent.parent / "data" / "family.sqlite"


def pull(adb: str) -> str:
    """The newest week*.csv in the phone's Download folder, as text. The phone must be unlocked and allow USB debugging."""
    listing = subprocess.run([adb, "shell", "ls", "-t", "/sdcard/Download/"], capture_output=True, text=True, timeout=20)
    if listing.returncode != 0:
        raise RuntimeError("the phone did not answer: " + listing.stderr.strip())
    names = [name.strip() for name in listing.stdout.splitlines() if name.strip().startswith("week") and name.strip().endswith(".csv")]
    if not names:
        raise RuntimeError("no week file in the phone's Download folder. On the phone: Family, Files, Save the week.")
    shown = subprocess.run([adb, "exec-out", "cat", "/sdcard/Download/" + names[0]], capture_output=True, timeout=20)
    if shown.returncode != 0:
        raise RuntimeError("the week file could not be read from the phone")
    return shown.stdout.decode("utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="stripsense_backend", description="StripSense family server")
    parser.add_argument("--db", default=str(DEFAULT_DB), help="the SQLite file (default: backend/data/family.sqlite)")
    commands = parser.add_subparsers(dest="command", required=True)
    serve = commands.add_parser("serve", help="start the server")
    serve.add_argument("--host", default="127.0.0.1", help="127.0.0.1 keeps it on this laptop; 0.0.0.0 lets the home network in")
    serve.add_argument("--port", type=int, default=8765)
    serve.add_argument("--token", default=None, help="when set, reading and sending need this token")
    serve.add_argument("--link", action="store_true", help="accept sealed messages from phones that have the family link on")
    relay = commands.add_parser("relay", help="carry sealed family messages and serve the family view; safe to put on the internet")
    relay.add_argument("--host", default="127.0.0.1")
    relay.add_argument("--port", type=int, default=8765)
    ingest = commands.add_parser("ingest", help="store one week file")
    ingest.add_argument("file")
    pulling = commands.add_parser("pull", help="fetch the newest week file from the phone over USB")
    pulling.add_argument("--adb", default=shutil.which("adb") or "adb")
    args = parser.parse_args(argv)

    database = store.connect(args.db)
    try:
        if args.command == "relay":
            # Only the family view and the sealed boxes. Nothing this server keeps can be read without a family's code.
            server = api.serve(database, args.host, args.port, relay_only=True)
            print(f"StripSense relay on http://{args.host}:{server.server_address[1]}  (Ctrl+C stops it)")
            print("It keeps one sealed message for each family and cannot open any of them.")
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
            return 0
        if args.command == "serve":
            if args.host != "127.0.0.1" and not args.token:
                print("Refusing to listen beyond this laptop without --token.", file=sys.stderr)
                return 2
            code = None
            if args.link:
                # One pairing code for this server, kept beside the data so it is the same after a restart.
                kept = Path(args.db).resolve().parent / "link-code.txt"
                kept.parent.mkdir(parents=True, exist_ok=True)
                if not kept.exists():
                    kept.write_text(link.new_code(), encoding="utf-8")
                code = kept.read_text(encoding="utf-8").strip()
            here = None
            if code:
                try:
                    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    probe.connect(("10.255.255.255", 1))
                    here = probe.getsockname()[0]
                    probe.close()
                except OSError:
                    here = None
            server = api.serve(database, args.host, args.port, args.token, code, here)
            port = server.server_address[1]
            print(f"StripSense family server on http://{args.host}:{port}  (Ctrl+C stops it)")
            if code:
                print("Family link is on. Open the family page on this computer, then on the phone:")
                print("  Family, Share, Connect to the family computer, and point the phone at the page.")
                if args.token:
                    print(f"  Open the page as: http://{here}:{port}/?token={args.token}")
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
            return 0
        text = Path(args.file).read_text(encoding="utf-8") if args.command == "ingest" else pull(args.adb)
        result = service.ingest(database, text)
        print(f"{result['person']}: {result['doses']} doses, " + ("stored." if result["new"] else "already stored, nothing changed."))
        return 0
    except (weekfile.WeekFileError, RuntimeError, OSError) as problem:
        print(f"Not stored: {problem}", file=sys.stderr)
        return 1
    finally:
        database.close()


if __name__ == "__main__":
    sys.exit(main())
