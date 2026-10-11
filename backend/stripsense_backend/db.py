"""The family server's own store: one SQLite file. Nothing here is on the phone, and nothing leaves this machine."""
from __future__ import annotations

import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS person (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    woman INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS export (
    id INTEGER PRIMARY KEY,
    person_id INTEGER NOT NULL REFERENCES person(id),
    exported_at TEXT NOT NULL,
    list_saved_at TEXT NOT NULL DEFAULT '',
    received_at TEXT NOT NULL,
    sha256 TEXT NOT NULL UNIQUE
);
-- One row for one dose of one medicine. A later export of the same dose replaces the earlier one.
CREATE TABLE IF NOT EXISTS dose (
    person_id INTEGER NOT NULL REFERENCES person(id),
    date TEXT NOT NULL,
    time TEXT NOT NULL,
    brand TEXT NOT NULL,
    nickname TEXT NOT NULL,
    strength TEXT NOT NULL,
    state TEXT NOT NULL,
    marked_at TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT '',
    exported_at TEXT NOT NULL,
    PRIMARY KEY (person_id, date, time, brand)
);
CREATE INDEX IF NOT EXISTS dose_by_day ON dose (person_id, date);
-- The relay: the latest sealed message of each family. The server has no key and cannot open one.
CREATE TABLE IF NOT EXISTS box (
    id TEXT PRIMARY KEY,
    sealed BLOB NOT NULL,
    received_at TEXT NOT NULL
);
-- The last thing each person's phone said about itself over the family link.
CREATE TABLE IF NOT EXISTS phone_status (
    person_id INTEGER PRIMARY KEY REFERENCES person(id),
    sent_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    battery INTEGER,
    charging INTEGER
);
"""


def connect(path: str | Path) -> sqlite3.Connection:
    """Opens the store, making it on first use. ':memory:' gives a store that is gone when closed, for tests."""
    if str(path) != ":memory:":
        Path(path).parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(str(path), check_same_thread=False)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys = ON")
    db.executescript(SCHEMA)
    return db
