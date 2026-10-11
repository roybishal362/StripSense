"""Run from the backend folder:  python -m unittest discover -s tests -v"""
import json
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from datetime import datetime, timedelta

from stripsense_backend import api, db, link, service, weekfile

SAMPLE = (Path(__file__).parent / "sample-week.csv").read_text(encoding="utf-8")
HEAD = "exported_at,2026-10-10T21:30,person,Maa,list_saved_at,2026-10-10T11:20,woman,true\n" + ",".join(weekfile.HEADER) + "\n"


class WeekFileTest(unittest.TestCase):
    def test_the_phones_own_sample_is_read(self):
        week = weekfile.parse(SAMPLE)
        self.assertEqual("Maa", week.person)
        self.assertTrue(week.woman)
        self.assertGreater(len(week.doses), 20)
        self.assertTrue(all(dose.state in weekfile.STATES for dose in week.doses))

    def test_anything_else_is_refused_whole(self):
        for bad in ["", "hello", HEAD.replace("nickname", "name"), HEAD + "2026-10-10,08:00,a,B,500 mg,swallowed,,\n",
                    HEAD + "2026-10-10,8am,a,B,500 mg,marked,08:01,prompt\n", HEAD + "2026-10-10,08:00,a,B\n"]:
            with self.assertRaises(weekfile.WeekFileError, msg=bad[:40]):
                weekfile.parse(bad)


class ServiceTest(unittest.TestCase):
    def setUp(self):
        self.db = db.connect(":memory:")

    def tearDown(self):
        self.db.close()

    def test_the_same_week_twice_changes_nothing(self):
        first = service.ingest(self.db, SAMPLE)
        again = service.ingest(self.db, SAMPLE)
        self.assertTrue(first["new"])
        self.assertFalse(again["new"])
        self.assertEqual(1, service.people(self.db)[0]["exports"])

    def test_an_older_export_never_overwrites_a_newer_one(self):
        row = "2026-10-10,08:00,fever tablet,FEVORA,500 mg,{state},{at},{source}\n"
        newer = HEAD + row.format(state="marked", at="08:05", source="prompt")
        older = HEAD.replace("21:30", "07:00") + row.format(state="later", at="", source="")
        service.ingest(self.db, newer)
        service.ingest(self.db, older)
        person = service.people(self.db)[0]["id"]
        self.assertEqual("marked", service.week(self.db, person)[-1]["doses"][0]["state"])

    def test_what_needs_a_look(self):
        text = HEAD + "".join([
            "2026-10-09,08:00,fever tablet,FEVORA,500 mg,not marked,,\n",
            "2026-10-09,14:00,fever tablet,FEVORA,500 mg,not sure,,\n",
            "2026-10-10,08:00,fever tablet,FEVORA,500 mg,marked,09:30,prompt\n",
            "2026-10-10,14:00,fever tablet,FEVORA,500 mg,marked,14:05,prompt\n",
            "2026-10-10,20:00,fever tablet,FEVORA,500 mg,later,,\n"])
        person = service.ingest(self.db, text)["person_id"]
        kinds = [alert["kind"] for alert in service.alerts(self.db, person)]
        self.assertEqual(["late", "not_sure", "not_marked"], kinds)
        counts = service.summary(self.db, person)
        self.assertEqual((2, 1, 1, 1), (counts["marked"], counts["not_marked"], counts["not_sure"], counts["later"]))
        self.assertEqual(0.5, counts["marked_share"])
        days = service.week(self.db, person)
        self.assertEqual(7, len(days))
        self.assertEqual("2026-10-10", days[-1]["date"])

    def test_no_wording_says_taken(self):
        page = (Path(api.WEB) / "index.html").read_text(encoding="utf-8").lower()
        self.assertNotIn("taken", page)


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.db = db.connect(":memory:")
        self.server = api.serve(self.db, port=0, token="secret")
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.db.close()

    def call(self, path, body=None, token=None):
        request = urllib.request.Request(self.base + path, data=body.encode("utf-8") if body is not None else None)
        if token:
            request.add_header("X-StripSense-Token", token)
        try:
            with urllib.request.urlopen(request, timeout=5) as reply:
                return reply.status, json.loads(reply.read().decode("utf-8")) if "json" in reply.headers["Content-Type"] else reply.read()
        except urllib.error.HTTPError as problem:
            return problem.code, json.loads(problem.read().decode("utf-8"))

    def test_a_week_goes_in_and_comes_back(self):
        self.assertEqual(200, self.call("/healthz")[0])
        self.assertEqual(401, self.call("/api/weeks", SAMPLE)[0])
        self.assertEqual(401, self.call("/api/weeks", SAMPLE, token="wrong")[0])
        status, stored = self.call("/api/weeks", SAMPLE, token="secret")
        self.assertEqual(201, status)
        person = stored["person_id"]
        self.assertEqual(401, self.call("/api/people")[0])
        self.assertEqual("Maa", self.call("/api/people", token="secret")[1][0]["name"])
        self.assertEqual(7, len(self.call(f"/api/people/{person}/week", token="secret")[1]))
        self.assertIn("marked_share", self.call(f"/api/people/{person}/summary", token="secret")[1])
        self.assertIsInstance(self.call(f"/api/people/{person}/alerts", token="secret")[1], list)
        self.assertIsNone(self.call(f"/api/people/{person}/phone", token="secret")[1])

    def test_bad_requests_are_told_why(self):
        self.assertEqual(400, self.call("/api/weeks", "not a week file", token="secret")[0])
        self.assertEqual(400, self.call("/api/people/1/week?end=tomorrow", token="secret")[0])
        self.assertEqual(404, self.call("/api/nothing", token="secret")[0])
        self.assertEqual(200, self.call("/files")[0])


class LinkTest(unittest.TestCase):
    CODE = "ABCD-EFGH-JKLM"

    def test_the_server_and_the_phone_make_the_same_key_and_message(self):
        # The same values are asserted in the app's LinkSealTest.
        self.assertEqual("b7b16df0c34bcb3a6c7cfefb9a78268479c4caa6f3121c1575dd49e94a3a3c40", link.key(self.CODE).hex())
        sealed = link.seal(b'{"battery":64}', link.key(self.CODE), bytes(range(12)))
        self.assertEqual("000102030405060708090a0b7b41553a56634c2cfffe7a986cde8efe15aa61d9d8db98f921a71b259885", sealed.hex())

    def message(self, sent_at="2026-10-10T13:20:00Z", battery=64, charging=True):
        return json.dumps({"sent_at": sent_at, "battery": battery, "charging": charging, "week": SAMPLE}).encode("utf-8")

    def test_a_sealed_message_brings_the_week_and_the_phones_state(self):
        store = db.connect(":memory:")
        opened = link.open_message(link.seal(self.message(), link.key(self.CODE)), link.key(self.CODE))
        heard = datetime(2026, 10, 10, 18, 50)
        person = service.link_in(store, opened, heard)["person_id"]
        self.assertEqual(7, len(service.week(store, person)))
        state = service.phone(store, person, heard + timedelta(minutes=2))
        self.assertEqual((64, True, True, 2), (state["battery"], state["charging"], state["online"], state["minutes_ago"]))
        self.assertFalse(service.phone(store, person, heard + timedelta(minutes=30))["online"])
        # An older message replayed later does not turn the phone's state back.
        service.link_in(store, json.loads(self.message("2026-10-10T09:00:00Z", battery=5, charging=False)), heard + timedelta(minutes=3))
        self.assertEqual(64, service.phone(store, person, heard + timedelta(minutes=3))["battery"])
        store.close()

    def test_a_wrong_code_or_a_changed_message_is_refused(self):
        sealed = link.seal(self.message(), link.key(self.CODE))
        with self.assertRaises(link.LinkError):
            link.open_message(sealed, link.key("ZZZZ-ZZZZ-ZZZZ"))
        with self.assertRaises(link.LinkError):
            link.open_message(sealed[:-1] + bytes([sealed[-1] ^ 1]), link.key(self.CODE))
        with self.assertRaises(link.LinkError):
            link.open_message(link.seal(b'{"hello": 1}', link.key(self.CODE)), link.key(self.CODE))

    def test_over_http_only_a_phone_with_the_code_gets_in_and_reads_need_the_token(self):
        store = db.connect(":memory:")
        server = api.serve(store, port=0, token="secret", link_code=self.CODE)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        base = f"http://127.0.0.1:{server.server_address[1]}"

        def post(blob):
            try:
                with urllib.request.urlopen(urllib.request.Request(base + "/api/link", data=blob), timeout=5) as reply:
                    return reply.status
            except urllib.error.HTTPError as problem:
                return problem.code

        def read(path, token=None):
            request = urllib.request.Request(base + path)
            if token:
                request.add_header("X-StripSense-Token", token)
            try:
                with urllib.request.urlopen(request, timeout=5) as reply:
                    return reply.status, json.loads(reply.read().decode("utf-8"))
            except urllib.error.HTTPError as problem:
                return problem.code, None

        try:
            self.assertEqual(401, post(link.seal(self.message(), link.key("WRONG-CODE-0000"))))
            self.assertEqual(201, post(link.seal(self.message(), link.key(self.CODE))))
            self.assertEqual(401, read("/api/people")[0])
            status, people = read("/api/people", "secret")
            self.assertEqual(200, status)
            self.assertEqual(64, read(f"/api/people/{people[0]['id']}/phone", "secret")[1]["battery"])
        finally:
            server.shutdown()
            server.server_close()
            store.close()

    def test_a_new_pairing_code_is_twelve_characters_with_no_lookalikes(self):
        code = link.new_code()
        self.assertEqual(14, len(code))
        self.assertFalse(set(code) & set("01IO"))


class RelayTest(unittest.TestCase):
    """The relay keeps a sealed box for a family and cannot read it. The phone and the family's page do the sealing."""

    def test_the_box_name_is_the_one_the_phone_and_the_page_work_out(self):
        self.assertEqual("3fa0b975068b4fbf5b3f326842af7322", link.box_id(link.key("ABCD-EFGH-JKLM")))

    def test_a_sealed_message_is_kept_and_handed_back_unchanged_with_no_token(self):
        store = db.connect(":memory:")
        server = api.serve(store, port=0, relay_only=True)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        base = f"http://127.0.0.1:{server.server_address[1]}"
        key = link.key("ABCD-EFGH-JKLM")
        # On the internet nothing else answers: not the people, not the file page, not a week sent in.
        for path in ("/api/people", "/files", "/api/pairing"):
            with self.assertRaises(urllib.error.HTTPError) as closed:
                urllib.request.urlopen(base + path, timeout=5)
            self.assertEqual(404, closed.exception.code)
        sealed = link.seal(b'{"sent_at":"2026-10-10T13:20:00Z","battery":64,"charging":true,"people":[]}', key)
        try:
            with self.assertRaises(urllib.error.HTTPError) as empty:
                urllib.request.urlopen(base + "/api/box/" + link.box_id(key), timeout=5)
            self.assertEqual(404, empty.exception.code)
            with urllib.request.urlopen(urllib.request.Request(base + "/api/box/" + link.box_id(key), data=sealed), timeout=5) as reply:
                self.assertEqual(201, reply.status)
            with urllib.request.urlopen(base + "/api/box/" + link.box_id(key), timeout=5) as reply:
                self.assertEqual(sealed, reply.read())
                self.assertTrue(reply.headers["X-Received-At"])
            # What the server keeps is not readable: the battery level is nowhere in it.
            kept = store.execute("SELECT sealed FROM box").fetchone()["sealed"]
            self.assertNotIn(b"battery", kept)
            with urllib.request.urlopen(base + "/", timeout=5) as page:
                self.assertIn(b"family view", page.read())
            with self.assertRaises(urllib.error.HTTPError) as bad:
                urllib.request.urlopen(urllib.request.Request(base + "/api/box/" + link.box_id(key), data=b"short"), timeout=5)
            self.assertEqual(400, bad.exception.code)
        finally:
            server.shutdown()
            server.server_close()
            store.close()


if __name__ == "__main__":
    unittest.main()
