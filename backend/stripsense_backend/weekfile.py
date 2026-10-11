"""Reads the week file the phone exports (week.csv, written by the app's WeekFile.kt).

The first line is pairs of key and value. The second is the column header. Every further line is one dose.
A file that does not have exactly this shape is refused whole: half a week is worse than no week.
"""
from __future__ import annotations

import csv
import io
import re
from dataclasses import dataclass

HEADER = ["date", "time", "nickname", "brand", "strength", "state", "marked_at", "source"]
STATES = {"marked", "not sure", "later", "not marked"}
_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_TIME = re.compile(r"^\d{2}:\d{2}$")
MAX_BYTES = 512 * 1024


class WeekFileError(ValueError):
    """The file is not a StripSense week file. The message says which line and why."""


@dataclass(frozen=True)
class Dose:
    date: str
    time: str
    nickname: str
    brand: str
    strength: str
    state: str
    marked_at: str
    source: str


@dataclass(frozen=True)
class Week:
    person: str
    woman: bool
    exported_at: str
    list_saved_at: str
    doses: tuple[Dose, ...]


def parse(text: str) -> Week:
    if len(text.encode("utf-8")) > MAX_BYTES:
        raise WeekFileError("the file is larger than a week file can be")
    rows = [row for row in csv.reader(io.StringIO(text.lstrip("﻿"))) if row]
    if len(rows) < 2:
        raise WeekFileError("the file has no header")
    first = rows[0]
    if len(first) % 2 != 0:
        raise WeekFileError("line 1 must be pairs of key and value")
    meta = dict(zip(first[0::2], first[1::2]))
    for key in ("exported_at", "person"):
        if not meta.get(key):
            raise WeekFileError(f"line 1 has no {key}")
    if rows[1] != HEADER:
        raise WeekFileError("line 2 is not the week file's column header")
    doses = []
    for number, row in enumerate(rows[2:], start=3):
        if len(row) != len(HEADER):
            raise WeekFileError(f"line {number} has {len(row)} columns, not {len(HEADER)}")
        dose = Dose(*[cell.strip() for cell in row])
        if not _DATE.match(dose.date) or not _TIME.match(dose.time):
            raise WeekFileError(f"line {number} has a date or time that cannot be read")
        if dose.state not in STATES:
            raise WeekFileError(f"line {number} has an unknown state: {dose.state!r}")
        if dose.marked_at and not _TIME.match(dose.marked_at):
            raise WeekFileError(f"line {number} has a marked time that cannot be read")
        doses.append(dose)
    return Week(meta["person"], meta.get("woman", "").lower() == "true", meta["exported_at"], meta.get("list_saved_at", ""), tuple(doses))
