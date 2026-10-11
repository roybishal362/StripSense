# StripSense family server

A small server for the family's laptop. It keeps the weeks the phone exports and shows what needs a look:
doses not marked, doses the person was not sure about, and doses marked late.

Python 3.10 or newer. Nothing to install: it uses only the standard library and SQLite.

## Why the phone does not talk to it directly

By default nothing leaves the phone. A week reaches the server because the family exports it on the phone
(Family, Files, Save the week) and it travels by the USB cable, or the family picks the file on the page.
Since 10 October 2026 there is also an optional family link, off until the family turns it on (see below).

## Run it

```
cd backend
python -m stripsense_backend serve
```

Open http://127.0.0.1:8765 and pick the week file. Or, with the phone on the cable:

```
python -m stripsense_backend pull --adb "E:\andrriod repo\platform-tools\adb.exe"
```

To let other machines on the home network open the page, a token is required:

```
python -m stripsense_backend serve --host 0.0.0.0 --token some-long-secret
```

## The relay: a son or daughter anywhere in the world

This is the part a family uses. The parent's phone seals one message with the family code and sends it here; the
family member's phone fetches it and opens it with the same code, in the page itself. This server keeps a sealed
box for each family and has no key to any of them.

```
python -m stripsense_backend relay
```

That listens on this computer only. To be reached from another country it has to be on the internet: run it on any
small hosted server, or, for a demonstration, put a tunnel in front of it, for example
`ssh -R 80:localhost:8765 nokey@localhost.run`, which prints an `https://...` address. The page needs https: a
browser will not do the unsealing over plain http.

On the parent's phone: Family, Share, "Turn this on", then "Send the link to your family". The family member taps
the link and sees the week. (Until the app is built with a fixed relay address, whoever is presenting sets it once:
long-press the "Medicines of ..." line in Family, then Share shows a field for it.)

| Route | Meaning |
| --- | --- |
| `GET /` | The family view. It reads the code after the `#` in the address, which a browser never sends anywhere. |
| `POST /api/box/<id>` | Keep this family's latest sealed message. |
| `GET /api/box/<id>` | Hand it back, unchanged. |

## The family link on a home network (older, kept for the file page)

```
python -m stripsense_backend serve --host 0.0.0.0 --token some-long-secret --link
```

The server prints its address and a pairing code. On the phone: Family, Settings, Family link. Type both, turn it
on, press "Save and send now". From then on the phone sends its battery level, whether it is charging and the week
of doses when the app is opened, within a minute of a mark, and at each reminder. The page shows whether the phone
is online and when it was last heard. Open the page from another machine as `http://<address>:8765/?token=<token>`.

Each message is sealed on the phone with AES-256-GCM under a key made from the pairing code, so it can cross a home
network over plain HTTP without being readable or changeable. This part needs one install: `pip install cryptography`.
The laptop's firewall must allow the port, and the phone and laptop must be on the same network.

## Parts

| File | What it does |
| --- | --- |
| `stripsense_backend/weekfile.py` | Reads and checks the phone's `week.csv`. A file with the wrong shape is refused whole. |
| `stripsense_backend/db.py` | The SQLite tables: person, export, dose. |
| `stripsense_backend/service.py` | Store a week, the last seven days, what needs a look, counts. |
| `stripsense_backend/api.py` | The HTTP routes and the token check. |
| `stripsense_backend/link.py` | Opens the sealed messages a phone sends over the family link. |
| `stripsense_backend/__main__.py` | The command line: `serve`, `ingest`, `pull`. |
| `stripsense_backend/web/index.html` | The family page. |
| `tests/test_backend.py` | 13 tests. `python -m unittest discover -s tests` |

## Routes

| Route | Meaning |
| --- | --- |
| `GET /healthz` | Is the server up. |
| `POST /api/weeks` | Body: the week file as text. Header `X-StripSense-Token` when a token is set. |
| `GET /api/people` | Everyone a week has been received for. |
| `GET /api/people/<id>/week` | The last seven days on record. `?end=2026-10-10` for an earlier week. |
| `GET /api/people/<id>/alerts` | Not marked, not sure, marked more than an hour late. |
| `GET /api/people/<id>/summary` | Counts and the share of doses marked. |

## What it does not do

- It never says a medicine was taken. "Marked" is what the person confirmed on the phone.
- It sends nothing anywhere. The data stays in `backend/data/family.sqlite` on this laptop.
- It has no accounts. Beyond this laptop the only protection is the token, sent in plain HTTP. Use it on a home network only.
