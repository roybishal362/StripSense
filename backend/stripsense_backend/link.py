"""The family link: what the phone sends by itself when the family has turned the link on.

The phone seals each message with a key made from the pairing code (PBKDF2, then AES-256-GCM), so the message
can travel over plain HTTP on a home network without being readable or changeable on the way. A message that
does not open with this server's code is refused. The same constants are in the app's LinkSeal.
"""
from __future__ import annotations

import hashlib
import json
import secrets

SALT = b"stripsense-family-link-v1"
BOUND = b"stripsense-link-v1"
MAX_BYTES = 700 * 1024


class LinkError(ValueError):
    """The message was not sealed with this server's pairing code, or is not a link message."""


def new_code() -> str:
    """A pairing code to type on the phone: twelve letters and digits with no look-alikes, in groups of four."""
    letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    raw = "".join(secrets.choice(letters) for _ in range(12))
    return "-".join(raw[i:i + 4] for i in range(0, 12, 4))


def key(code: str) -> bytes:
    clean = "".join(ch for ch in code if ch.isalnum()).upper()
    return hashlib.pbkdf2_hmac("sha256", clean.encode(), SALT, 100_000, 32)


def box_id(code_key: bytes) -> str:
    """Where on the relay a family's messages are kept. Worked out from the key; useless for opening them."""
    return hashlib.sha256(code_key + b"box").hexdigest()[:32]


def _aes():
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError as missing:  # the only part of the server that needs an install
        raise LinkError("the family link needs the 'cryptography' package: pip install cryptography") from missing
    return AESGCM


def seal(plain: bytes, code_key: bytes, nonce: bytes | None = None) -> bytes:
    nonce = nonce or secrets.token_bytes(12)
    return nonce + _aes()(code_key).encrypt(nonce, plain, BOUND)


def open_message(blob: bytes, code_key: bytes) -> dict:
    if len(blob) < 12 + 16 or len(blob) > MAX_BYTES:
        raise LinkError("not a link message")
    try:
        plain = _aes()(code_key).decrypt(blob[:12], blob[12:], BOUND)
    except LinkError:
        raise
    except Exception as wrong:  # a wrong code and a changed message look the same, on purpose
        raise LinkError("the message does not open with this server's pairing code") from wrong
    try:
        message = json.loads(plain.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as bad:
        raise LinkError("the message is not readable") from bad
    if not isinstance(message, dict) or not isinstance(message.get("week"), str) or not isinstance(message.get("sent_at"), str):
        raise LinkError("the message is not a link message")
    return message
