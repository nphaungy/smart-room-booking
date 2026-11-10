"""E-ticket check-in (staff scan the booker's QR) and the no-show record."""
import pytest

from app.db import apply_schema, init_pool
from app.services.sweeper import run_sweep

from .conftest import TEST_DB


def code(r):
    return r.json()["error"]["code"]


def sweep():
    with init_pool().connection() as conn:
        run_sweep(conn)


@pytest.fixture
def approved_at(db):
    """An approved booking for student1 (user 3) starting `minutes` from now."""
    def _make(minutes, required=True, room_id=1):
        return db.execute(
            "INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose, status, checkin_required) "
            "VALUES (%s, 3, now() + make_interval(mins => %s), now() + make_interval(mins => %s), 5, 'x', 'APPROVED', %s) "
            "RETURNING id",
            (room_id, minutes, minutes + 60, required),
        ).fetchone()[0]
    return _make


@pytest.fixture
def ticket(db):
    """The booking's ticket code, read straight from the database (what the booker's QR contains)."""
    def _ticket(booking_id):
        return db.execute("SELECT ticket_code FROM bookings WHERE id = %s", (booking_id,)).fetchone()[0]
    return _ticket


@pytest.fixture
def scan(client, h):
    def _scan(code, who="approver"):
        return client.post("/api/checkin/scan", headers=h[who], json={"code": code})
    return _scan


# ------------------------------------------------------------------ the e-ticket itself
def test_ticket_is_shown_only_to_the_owner_once_approved(client, h, book):
    b = book().json()
    assert b["ticket_code"] is None  # still pending: no ticket yet
    client.post(f"/api/bookings/{b['id']}/approve", headers=h["approver"])

    mine = client.get(f"/api/bookings/{b['id']}", headers=h["s1"]).json()
    assert mine["ticket_code"] and len(mine["ticket_code"]) == 12
    assert client.get("/api/bookings/me", headers=h["s1"]).json()[0]["ticket_code"] == mine["ticket_code"]
    # staff never receive it, so nobody can check a booker in without the booker's phone
    assert client.get(f"/api/bookings/{b['id']}", headers=h["approver"]).json()["ticket_code"] is None
    assert client.get(f"/api/bookings/{b['id']}", headers=h["admin"]).json()["ticket_code"] is None
    assert all(x["ticket_code"] is None for x in client.get("/api/bookings", headers=h["approver"]).json())
    assert client.get(f"/api/bookings/{b['id']}", headers=h["s2"]).status_code == 403


def test_every_booking_gets_its_own_ticket(approved_at, ticket):
    codes = {ticket(approved_at(5 + 120 * i)) for i in range(5)}
    assert len(codes) == 5


def test_ticket_disappears_when_booking_is_cancelled(client, h, book):
    b = book().json()
    client.post(f"/api/bookings/{b['id']}/approve", headers=h["approver"])
    client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s1"], json={})
    assert client.get(f"/api/bookings/{b['id']}", headers=h["s1"]).json()["ticket_code"] is None


# ------------------------------------------------------------------ scanning
def test_staff_scan_checks_the_booker_in(client, h, approved_at, ticket, scan):
    bid = approved_at(5)
    before = client.get(f"/api/bookings/{bid}", headers=h["s1"]).json()
    assert before["checkin_deadline"] and before["checked_in_at"] is None

    r = scan(ticket(bid))
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["id"] == bid and body["booker_name"] and body["room_code"]  # the scanner shows who and where
    assert body["checked_in_at"] and body["checkin_method"] == "SCAN" and body["checkin_deadline"] is None
    assert "BOOKING_CHECKED_IN" in [n["type"] for n in client.get("/api/notifications", headers=h["s1"]).json()]

    again = scan(ticket(bid))
    assert again.status_code == 409 and code(again) == "ALREADY_CHECKED_IN"


def test_admin_can_scan_too(approved_at, ticket, scan):
    assert scan(ticket(approved_at(5)), who="admin").status_code == 200


def test_booker_cannot_scan_even_their_own_ticket(approved_at, ticket, scan):
    bid = approved_at(5)
    assert scan(ticket(bid), who="s1").status_code == 403
    assert scan(ticket(bid), who="s2").status_code == 403


@pytest.mark.parametrize("shape", ["roombooking://ticket/{}", "  {}  ", "ROOMBOOKING://TICKET/{}"])
def test_scan_accepts_full_qr_content_and_whitespace(approved_at, ticket, scan, shape):
    r = scan(shape.format(ticket(approved_at(5))))
    assert r.status_code == 200, r.text


def test_scan_is_case_insensitive(approved_at, ticket, scan):
    r = scan(ticket(approved_at(5)).lower())
    assert r.status_code == 200, r.text


def test_scan_rejects_unknown_or_foreign_codes(scan):
    unknown = scan("ABCDEF123456")
    assert unknown.status_code == 404 and code(unknown) == "TICKET_NOT_FOUND"
    foreign = scan("https://example.com/some-other-qr")
    assert foreign.status_code == 422 and code(foreign) == "INVALID_TICKET"


def test_scan_time_window(approved_at, ticket, scan):
    early = scan(ticket(approved_at(60)))
    assert early.status_code == 409 and code(early) == "CHECKIN_TOO_EARLY"
    late = scan(ticket(approved_at(-20, room_id=2)))
    assert late.status_code == 409 and code(late) == "CHECKIN_TOO_LATE"


def test_pending_or_cancelled_booking_cannot_be_scanned(client, h, book, ticket, scan):
    pending = book().json()
    r = scan(ticket(pending["id"]))
    assert r.status_code == 409 and code(r) == "INVALID_STATUS"
    client.post(f"/api/bookings/{pending['id']}/cancel", headers=h["s1"], json={})
    r = scan(ticket(pending["id"]))
    assert r.status_code == 409 and code(r) == "INVALID_STATUS"


def test_room_qr_endpoints_are_gone(client, h):
    assert client.get("/api/admin/rooms/1/checkin-code", headers=h["admin"]).status_code == 404
    assert client.post("/api/bookings/1/check-in", headers=h["s1"], json={"code": "x"}).status_code in (404, 405)
    assert "checkin_code" not in client.get("/api/rooms/1", headers=h["s1"]).json()


def test_sweep_records_no_show_and_frees_slot(client, h, approved_at, db):
    bid = approved_at(-20)
    sweep()
    b = client.get(f"/api/bookings/{bid}", headers=h["s1"]).json()
    assert b["status"] == "NO_SHOW"
    assert b["logs"][-1]["changed_by_name"] == "ระบบ" and b["logs"][-1]["to_status"] == "NO_SHOW"
    assert not b["can_cancel"] and not b["can_edit"] and b["ticket_code"] is None
    assert "BOOKING_NO_SHOW" in [n["type"] for n in client.get("/api/notifications", headers=h["s1"]).json()]
    # would raise ExclusionViolation if the no-show still held the room
    db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
               "VALUES (1, 4, now() + interval '10 min', now() + interval '20 min', 1, 'y')")


def test_sweep_leaves_everything_else_alone(client, h, approved_at, ticket, scan):
    inside_grace = approved_at(-10)
    checked_in = approved_at(5, room_id=2)
    assert scan(ticket(checked_in)).status_code == 200
    exempt = approved_at(-500, required=False, room_id=3)
    sweep()
    for bid in (inside_grace, checked_in, exempt):
        assert client.get(f"/api/bookings/{bid}", headers=h["admin"]).json()["status"] == "APPROVED"


def test_sweep_is_idempotent(client, h, approved_at):
    bid = approved_at(-20)
    sweep()
    sweep()
    logs = client.get(f"/api/bookings/{bid}", headers=h["s1"]).json()["logs"]
    assert len([l for l in logs if l["to_status"] == "NO_SHOW"]) == 1


def test_scanning_after_no_show_points_staff_to_manual_confirm(approved_at, ticket, scan):
    bid = approved_at(-20)
    sweep()
    r = scan(ticket(bid))
    assert r.status_code == 409 and code(r) == "CHECKIN_TOO_LATE"


def test_staff_can_correct_a_no_show(client, h, approved_at):
    bid = approved_at(-20)
    sweep()
    assert client.get(f"/api/bookings/{bid}", headers=h["approver"]).json()["can_staff_check_in"]
    assert client.post(f"/api/bookings/{bid}/staff-check-in", headers=h["s1"], json={"note": "x"}).status_code == 403
    assert client.post(f"/api/bookings/{bid}/staff-check-in", headers=h["approver"], json={"note": "   "}).status_code == 422

    r = client.post(f"/api/bookings/{bid}/staff-check-in", headers=h["approver"], json={"note": "มาใช้จริง"})
    assert r.status_code == 200, r.text
    assert r.json()["status"] == "APPROVED" and r.json()["checkin_method"] == "STAFF"
    sweep()  # a corrected booking must not be recorded again
    assert client.get(f"/api/bookings/{bid}", headers=h["s1"]).json()["status"] == "APPROVED"


def test_staff_correction_fails_cleanly_when_slot_was_rebooked(client, h, approved_at, db):
    bid = approved_at(-20)
    sweep()
    db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
               "VALUES (1, 4, now() + interval '10 min', now() + interval '20 min', 1, 'y')")
    r = client.post(f"/api/bookings/{bid}/staff-check-in", headers=h["approver"], json={"note": "มาจริง"})
    assert r.status_code == 409 and code(r) == "SLOT_TAKEN"
    assert client.get(f"/api/bookings/{bid}", headers=h["approver"]).json()["status"] == "NO_SHOW"


def test_staff_cannot_confirm_before_window(client, h, approved_at):
    r = client.post(f"/api/bookings/{approved_at(60)}/staff-check-in", headers=h["approver"], json={"note": "x"})
    assert r.status_code == 409 and code(r) == "CHECKIN_TOO_EARLY"


def test_no_show_filter_for_staff(client, h, approved_at):
    bid = approved_at(-20)
    sweep()
    rows = client.get("/api/bookings?status=NO_SHOW", headers=h["approver"]).json()
    assert [x["id"] for x in rows] == [bid]


def test_upgrade_does_not_mark_old_bookings_as_no_show(db):
    """Upgrading a database with history: past bookings are exempt, upcoming ones need check-in."""
    insert = ("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose, status) "
              "VALUES (1, 3, now() + %s::interval, now() + %s::interval + interval '1 hour', 5, 'x', 'APPROVED') RETURNING id")
    past = db.execute(insert, ("-3 day", "-3 day")).fetchone()[0]
    future = db.execute(insert, ("3 day", "3 day")).fetchone()[0]
    db.execute("ALTER TABLE bookings DROP COLUMN checkin_required")
    apply_schema(TEST_DB)  # the column returns, as it would on the first start after upgrading
    assert db.execute("SELECT checkin_required FROM bookings WHERE id = %s", (past,)).fetchone()[0] is False
    assert db.execute("SELECT checkin_required FROM bookings WHERE id = %s", (future,)).fetchone()[0] is True
    sweep()
    assert db.execute("SELECT status FROM bookings WHERE id = %s", (past,)).fetchone()[0] == "APPROVED"


def test_upgrade_gives_old_bookings_their_own_tickets_and_drops_room_codes(db):
    """A database from the room-QR version: old bookings must each get a distinct ticket on the next start."""
    db.execute("ALTER TABLE bookings DROP COLUMN ticket_code")
    db.execute("ALTER TABLE rooms ADD COLUMN checkin_code TEXT")
    for i in range(3):
        db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
                   "VALUES (1, 3, now() + make_interval(days => %s), now() + make_interval(days => %s, hours => 1), 1, 'x')",
                   (i + 1, i + 1))
    apply_schema(TEST_DB)
    codes = [r[0] for r in db.execute("SELECT ticket_code FROM bookings").fetchall()]
    assert len(codes) == 3 and len(set(codes)) == 3 and all(len(c) == 12 for c in codes)
    assert db.execute("SELECT count(*) FROM information_schema.columns "
                      "WHERE table_name = 'rooms' AND column_name = 'checkin_code'").fetchone()[0] == 0
