"""QR check-in and the no-show record."""
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
def qr(client, h):
    def _qr(room_id=1):
        return client.get(f"/api/admin/rooms/{room_id}/checkin-code", headers=h["admin"]).json()["checkin_code"]
    return _qr


def test_check_in_succeeds_inside_window(client, h, approved_at, qr):
    bid = approved_at(5)
    before = client.get(f"/api/bookings/{bid}", headers=h["s1"]).json()
    assert before["can_check_in"] and before["checkin_deadline"] and before["checked_in_at"] is None

    r = client.post(f"/api/bookings/{bid}/check-in", headers=h["s1"], json={"code": qr()})
    assert r.status_code == 200, r.text
    assert r.json()["checked_in_at"] and r.json()["checkin_method"] == "SELF"
    assert not r.json()["can_check_in"] and r.json()["checkin_deadline"] is None

    again = client.post(f"/api/bookings/{bid}/check-in", headers=h["s1"], json={"code": qr()})
    assert again.status_code == 409 and code(again) == "ALREADY_CHECKED_IN"


@pytest.mark.parametrize("shape", ["roombooking://checkin?code={}", "  {}  "])
def test_check_in_accepts_full_qr_content_and_whitespace(client, h, approved_at, qr, shape):
    r = client.post(f"/api/bookings/{approved_at(5)}/check-in", headers=h["s1"], json={"code": shape.format(qr())})
    assert r.status_code == 200, r.text


def test_check_in_is_case_insensitive(client, h, approved_at, qr):
    r = client.post(f"/api/bookings/{approved_at(5)}/check-in", headers=h["s1"], json={"code": qr().lower()})
    assert r.status_code == 200, r.text


def test_check_in_rejects_code_of_another_room(client, h, approved_at, qr):
    r = client.post(f"/api/bookings/{approved_at(5)}/check-in", headers=h["s1"], json={"code": qr(2)})
    assert r.status_code == 422 and code(r) == "WRONG_ROOM_CODE"


def test_check_in_time_window(client, h, approved_at, qr):
    early = client.post(f"/api/bookings/{approved_at(60)}/check-in", headers=h["s1"], json={"code": qr()})
    assert early.status_code == 409 and code(early) == "CHECKIN_TOO_EARLY"
    late = client.post(f"/api/bookings/{approved_at(-20, room_id=2)}/check-in", headers=h["s1"], json={"code": qr(2)})
    assert late.status_code == 409 and code(late) == "CHECKIN_TOO_LATE"


def test_only_owner_can_check_in(client, h, approved_at, qr):
    bid = approved_at(5)
    assert client.post(f"/api/bookings/{bid}/check-in", headers=h["s2"], json={"code": qr()}).status_code == 403
    assert client.post(f"/api/bookings/{bid}/check-in", headers=h["approver"], json={"code": qr()}).status_code == 403


def test_pending_booking_cannot_check_in(client, h, book, qr):
    b = book().json()
    r = client.post(f"/api/bookings/{b['id']}/check-in", headers=h["s1"], json={"code": qr()})
    assert r.status_code == 409 and code(r) == "INVALID_STATUS"


def test_code_is_admin_only_and_never_public(client, h):
    assert client.get("/api/admin/rooms/1/checkin-code", headers=h["approver"]).status_code == 403
    assert client.post("/api/admin/rooms/1/checkin-code/rotate", headers=h["s1"]).status_code == 403
    assert "checkin_code" not in client.get("/api/rooms/1", headers=h["s1"]).json()
    assert "checkin_code" not in client.get("/api/rooms", headers=h["s1"]).json()[0]


def test_rotating_code_invalidates_the_old_one(client, h, approved_at, qr):
    old = qr()
    new = client.post("/api/admin/rooms/1/checkin-code/rotate", headers=h["admin"]).json()["checkin_code"]
    assert new != old and len(new) == 10
    bid = approved_at(5)
    assert code(client.post(f"/api/bookings/{bid}/check-in", headers=h["s1"], json={"code": old})) == "WRONG_ROOM_CODE"
    assert client.post(f"/api/bookings/{bid}/check-in", headers=h["s1"], json={"code": new}).status_code == 200


def test_sweep_records_no_show_and_frees_slot(client, h, approved_at, db):
    bid = approved_at(-20)
    sweep()
    b = client.get(f"/api/bookings/{bid}", headers=h["s1"]).json()
    assert b["status"] == "NO_SHOW"
    assert b["logs"][-1]["changed_by_name"] == "ระบบ" and b["logs"][-1]["to_status"] == "NO_SHOW"
    assert not b["can_cancel"] and not b["can_edit"] and not b["can_check_in"]
    assert "BOOKING_NO_SHOW" in [n["type"] for n in client.get("/api/notifications", headers=h["s1"]).json()]
    # would raise ExclusionViolation if the no-show still held the room
    db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
               "VALUES (1, 4, now() + interval '10 min', now() + interval '20 min', 1, 'y')")


def test_sweep_leaves_everything_else_alone(client, h, approved_at, qr):
    inside_grace = approved_at(-10)
    checked_in = approved_at(5, room_id=2)
    client.post(f"/api/bookings/{checked_in}/check-in", headers=h["s1"], json={"code": qr(2)})
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


def test_booker_arriving_after_no_show_is_told_to_see_staff(client, h, approved_at, qr):
    bid = approved_at(-20)
    sweep()
    r = client.post(f"/api/bookings/{bid}/check-in", headers=h["s1"], json={"code": qr()})
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
