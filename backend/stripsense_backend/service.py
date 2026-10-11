"""What the family server does with a week: keep it, show it, and say what needs a look.

It reports what the phone recorded. It never says a medicine was taken; the phone's word is "marked",
and a mark is what the person confirmed on the phone.
"""
from __future__ import annotations

import hashlib
import sqlite3
import threading
from datetime import datetime, timedelta

from . import weekfile

LATE_MINUTES = 60
_lock = threading.Lock()


def ingest(db: sqlite3.Connection, text: str, received_at: datetime | None = None) -> dict:
    """Stores one exported week. The same file twice changes nothing. An older export never overwrites a newer one."""
    week = weekfile.parse(text)
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    stamp = (received_at or datetime.now()).isoformat(timespec="seconds")
    with _lock, db:
        db.execute("INSERT INTO person (name, woman) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET woman = excluded.woman",
                   (week.person, int(week.woman)))
        person_id = db.execute("SELECT id FROM person WHERE name = ?", (week.person,)).fetchone()["id"]
        if db.execute("SELECT 1 FROM export WHERE sha256 = ?", (digest,)).fetchone():
            return {"person": week.person, "person_id": person_id, "doses": len(week.doses), "new": False}
        db.execute("INSERT INTO export (person_id, exported_at, list_saved_at, received_at, sha256) VALUES (?, ?, ?, ?, ?)",
                   (person_id, week.exported_at, week.list_saved_at, stamp, digest))
        for dose in week.doses:
            db.execute(
                """INSERT INTO dose (person_id, date, time, brand, nickname, strength, state, marked_at, source, exported_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(person_id, date, time, brand) DO UPDATE SET
                       nickname = excluded.nickname, strength = excluded.strength, state = excluded.state,
                       marked_at = excluded.marked_at, source = excluded.source, exported_at = excluded.exported_at
                   WHERE excluded.exported_at >= dose.exported_at""",
                (person_id, dose.date, dose.time, dose.brand, dose.nickname, dose.strength, dose.state, dose.marked_at, dose.source, week.exported_at))
    return {"person": week.person, "person_id": person_id, "doses": len(week.doses), "new": True}


def people(db: sqlite3.Connection) -> list[dict]:
    rows = db.execute(
        """SELECT person.id, person.name, person.woman, MAX(export.exported_at) AS last_export, COUNT(export.id) AS exports
           FROM person LEFT JOIN export ON export.person_id = person.id GROUP BY person.id ORDER BY person.name""").fetchall()
    return [dict(row) for row in rows]


def week(db: sqlite3.Connection, person_id: int, end: str | None = None) -> list[dict]:
    """The seven days ending on [end] (the last day on record when not given), each with its doses in time order."""
    last = end or db.execute("SELECT MAX(date) AS d FROM dose WHERE person_id = ?", (person_id,)).fetchone()["d"]
    if not last:
        return []
    final = datetime.strptime(last, "%Y-%m-%d").date()
    days = [(final - timedelta(days=back)).isoformat() for back in range(6, -1, -1)]
    rows = db.execute("SELECT * FROM dose WHERE person_id = ? AND date BETWEEN ? AND ? ORDER BY date, time, nickname",
                      (person_id, days[0], days[-1])).fetchall()
    return [{"date": day, "doses": [_dose(row) for row in rows if row["date"] == day]} for day in days]


def alerts(db: sqlite3.Connection, person_id: int) -> list[dict]:
    """What the family should look at, newest first: doses not marked, doses the person was not sure about, late marks."""
    found = []
    for row in db.execute("SELECT * FROM dose WHERE person_id = ? ORDER BY date DESC, time DESC", (person_id,)):
        kind = None
        if row["state"] == "not marked":
            kind = "not_marked"
        elif row["state"] == "not sure":
            kind = "not_sure"
        elif row["state"] == "marked" and row["marked_at"] and _minutes(row["marked_at"]) - _minutes(row["time"]) > LATE_MINUTES:
            kind = "late"
        if kind:
            found.append({"kind": kind, **_dose(row), "date": row["date"]})
    return found


def summary(db: sqlite3.Connection, person_id: int) -> dict:
    """Counts over everything on record. "later" doses have not come up yet and are left out of the share."""
    counts = {state: 0 for state in weekfile.STATES}
    for row in db.execute("SELECT state, COUNT(*) AS n FROM dose WHERE person_id = ? GROUP BY state", (person_id,)):
        counts[row["state"]] = row["n"]
    came_up = counts["marked"] + counts["not marked"] + counts["not sure"]
    return {"marked": counts["marked"], "not_marked": counts["not marked"], "not_sure": counts["not sure"], "later": counts["later"],
            "marked_share": round(counts["marked"] / came_up, 3) if came_up else None}


def _dose(row: sqlite3.Row) -> dict:
    return {key: row[key] for key in ("time", "nickname", "brand", "strength", "state", "marked_at", "source")}


def _minutes(hhmm: str) -> int:
    hours, minutes = hhmm.split(":")
    return int(hours) * 60 + int(minutes)


def link_in(db: sqlite3.Connection, message: dict, received_at: datetime | None = None) -> dict:
    """One opened family-link message: the week is stored as any week is, and the phone's own state is kept beside it.
    A message older than the last one from the same phone changes nothing, so a replayed message cannot turn the clock back."""
    result = ingest(db, message["week"], received_at)
    stamp = (received_at or datetime.now()).isoformat(timespec="seconds")
    battery = message.get("battery")
    battery = battery if isinstance(battery, int) and 0 <= battery <= 100 else None
    with _lock, db:
        db.execute(
            """INSERT INTO phone_status (person_id, sent_at, received_at, battery, charging) VALUES (?, ?, ?, ?, ?)
               ON CONFLICT(person_id) DO UPDATE SET sent_at = excluded.sent_at, received_at = excluded.received_at,
                   battery = excluded.battery, charging = excluded.charging
               WHERE excluded.sent_at > phone_status.sent_at""",
            (result["person_id"], message["sent_at"], stamp, battery, int(bool(message.get("charging")))))
    return result


def phone(db: sqlite3.Connection, person_id: int, now: datetime | None = None) -> dict | None:
    """What the person's phone last said about itself, and how long ago this server heard from it."""
    row = db.execute("SELECT * FROM phone_status WHERE person_id = ?", (person_id,)).fetchone()
    if not row:
        return None
    heard = datetime.fromisoformat(row["received_at"])
    minutes = max(0, int(((now or datetime.now()) - heard).total_seconds() // 60))
    return {"battery": row["battery"], "charging": bool(row["charging"]), "heard_at": row["received_at"], "minutes_ago": minutes,
            "online": minutes <= ONLINE_MINUTES}


ONLINE_MINUTES = 5

