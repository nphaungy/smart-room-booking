"""Monthly report: JSON and CSV. Uses a fixed past month because a report only reads rows."""
import csv
import io
from datetime import datetime
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Asia/Bangkok")


def insert(db, *, room=1, user=3, day=10, hours=2, status="APPROVED", checked=False):
    start = datetime(2026, 3, day, 9, tzinfo=TZ)
    end = datetime(2026, 3, day, 9 + hours, tzinfo=TZ)
    return db.execute(
        "INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose, status, reject_reason, "
        "checked_in_at, checkin_method) VALUES (%s, %s, %s, %s, 5, 'x', %s, %s, %s, %s) RETURNING id",
        (room, user, start, end, status, "r" if status == "REJECTED" else None,
         start if checked else None, "SELF" if checked else None),
    ).fetchone()[0]


def test_summary_rooms_and_users(client, h, db):
    insert(db, day=10, checked=True)  # 2h booked and used
    insert(db, day=11, status="NO_SHOW")  # 2h booked, none used
    insert(db, day=12, user=4, status="NO_SHOW", room=2)
    insert(db, day=13, user=4, status="NO_SHOW", room=2)
    insert(db, day=14, status="REJECTED")
    insert(db, day=15, status="CANCELLED")
    insert(db, day=16, status="PENDING")

    r = client.get("/api/reports/monthly?year=2026&month=3", headers=h["approver"])
    assert r.status_code == 200, r.text
    d = r.json()
    s = d["summary"]
    assert s["total_requests"] == 7 and s["approved"] == 4 and s["no_shows"] == 3 and s["checked_in"] == 1
    assert s["pending"] == 1 and s["rejected"] == 1 and s["cancelled"] == 1
    assert s["attendance_rate_percent"] == 25.0
    assert s["booked_hours"] == 8.0 and s["used_hours"] == 2.0

    room1 = next(x for x in d["rooms"] if x["room_id"] == 1)
    assert room1["approved"] == 2 and room1["checked_in"] == 1 and room1["no_shows"] == 1
    assert room1["booked_hours"] == 4.0 and room1["used_hours"] == 2.0
    assert room1["utilization_percent"] == round(100 * 4 / (31 * 12), 2)

    assert [(u["full_name"], u["no_shows"]) for u in d["no_show_users"]] == [("สมหญิง รักเรียน", 2), ("สมชาย ใจดี", 1)]


def test_month_boundaries_use_bangkok_time(client, h, db):
    for when in (datetime(2026, 3, 1, 0, 30, tzinfo=TZ), datetime(2026, 3, 31, 23, 0, tzinfo=TZ),
                 datetime(2026, 2, 28, 23, 30, tzinfo=TZ), datetime(2026, 4, 1, 0, 0, tzinfo=TZ)):
        db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
                   "VALUES (1, 3, %s, %s::timestamptz + interval '10 min', 5, 'x')", (when, when))
    d = client.get("/api/reports/monthly?year=2026&month=3", headers=h["admin"]).json()
    assert d["summary"]["total_requests"] == 2


def test_december_rolls_over_to_next_year(client, h, db):
    when = datetime(2026, 12, 31, 12, tzinfo=TZ)
    db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
               "VALUES (1, 3, %s, %s::timestamptz + interval '10 min', 5, 'x')", (when, when))
    assert client.get("/api/reports/monthly?year=2026&month=12", headers=h["admin"]).json()["summary"]["total_requests"] == 1


def test_empty_month_has_no_rate(client, h):
    d = client.get("/api/reports/monthly?year=2026&month=12", headers=h["admin"]).json()
    assert d["summary"]["total_requests"] == 0 and d["summary"]["attendance_rate_percent"] is None
    assert d["no_show_users"] == [] and d["waitlist"]["joined"] == 0
    assert len(d["rooms"]) == 7  # active rooms are still listed


def test_waitlist_counts(client, h, db):
    db.execute("INSERT INTO waitlist_entries (room_id, user_id, start_time, end_time, attendees, purpose, status) VALUES "
               "(1, 3, '2026-03-10 09:00+07', '2026-03-10 10:00+07', 1, 'x', 'EXPIRED'),"
               "(1, 4, '2026-03-10 09:00+07', '2026-03-10 10:00+07', 1, 'x', 'PROMOTED')")
    q = client.get("/api/reports/monthly?year=2026&month=3", headers=h["admin"]).json()["waitlist"]
    assert q["joined"] == 2 and q["expired"] == 1 and q["promoted"] == 1


def test_permissions_and_validation(client, h):
    assert client.get("/api/reports/monthly?year=2026&month=3", headers=h["s1"]).status_code == 403
    assert client.get("/api/reports/monthly.csv?year=2026&month=3", headers=h["s1"]).status_code == 403
    assert client.get("/api/reports/monthly?year=2026&month=3").status_code == 401
    assert client.get("/api/reports/monthly?year=2026&month=13", headers=h["admin"]).status_code == 422


def test_csv_opens_as_thai_in_excel_and_blocks_formulas(client, h, db):
    uid = db.execute("INSERT INTO users (email, password_hash, full_name) VALUES ('evil@example.com', 'x', '=HYPERLINK(1)') "
                     "RETURNING id").fetchone()[0]
    try:
        insert(db, user=uid, status="NO_SHOW")
        r = client.get("/api/reports/monthly.csv?year=2026&month=3", headers=h["admin"])
        assert r.status_code == 200 and r.headers["content-type"].startswith("text/csv")
        assert 'filename="room-report-2026-03.csv"' in r.headers["content-disposition"]
        assert r.content.startswith(b"\xef\xbb\xbf")
        rows = list(csv.reader(io.StringIO(r.content.decode("utf-8-sig"))))
        assert ["ไม่มาใช้ห้อง", "1"] in rows
        cells = [c for row in rows for c in row]
        assert "'=HYPERLINK(1)" in cells and "=HYPERLINK(1)" not in cells
    finally:
        db.execute("DELETE FROM bookings WHERE booker_id = %s", (uid,))
        db.execute("DELETE FROM users WHERE id = %s", (uid,))


def test_dashboard_counts_no_show_as_approved(client, h, db):
    insert(db, status="NO_SHOW")
    insert(db, day=11, status="REJECTED")
    d = client.get("/api/dashboard?date_from=2026-03-01&date_to=2026-03-31", headers=h["admin"]).json()
    assert d["approval_rate_percent"] == 50.0
