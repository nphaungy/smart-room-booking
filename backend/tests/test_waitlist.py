"""Waitlist: joining, automatic promotion, expiry."""
from datetime import timedelta

from app.db import init_pool
from app.services import waitlist_service
from app.services.sweeper import run_sweep

from .conftest import slot


def code(r):
    return r.json()["error"]["code"]


def sweep():
    with init_pool().connection() as conn:
        run_sweep(conn)


def join(client, h, who="s2", room_id=1, start=None, end=None, attendees=5):
    return client.post("/api/waitlist", headers=h[who], json={
        "room_id": room_id, "start_time": start or slot(1, 9), "end_time": end or slot(1, 10),
        "attendees": attendees, "purpose": "ติวหนังสือ",
    })


def test_join_requires_a_taken_slot(client, h, book):
    r = join(client, h)
    assert r.status_code == 409 and code(r) == "SLOT_AVAILABLE"
    book()
    r = join(client, h)
    assert r.status_code == 201, r.text
    assert r.json()["status"] == "WAITING" and r.json()["position"] == 1


def test_cannot_join_own_slot_or_twice(client, h, book):
    book()
    r = join(client, h, who="s1")
    assert r.status_code == 409 and code(r) == "ALREADY_BOOKED"
    assert join(client, h).status_code == 201
    dup = join(client, h)
    assert dup.status_code == 409 and code(dup) == "ALREADY_IN_WAITLIST"


def test_join_applies_booking_rules(client, h, book):
    book()
    assert code(join(client, h, attendees=999)) == "OVER_CAPACITY"
    assert code(join(client, h, start=slot(-1, 9), end=slot(-1, 10))) == "PAST_TIME"


def test_join_too_close_to_start_is_refused(client, h, book, monkeypatch):
    book()
    # Moves the cutoff instead of the clock, so this holds at any time of day.
    monkeypatch.setattr(waitlist_service, "_cutoff", lambda: waitlist_service.now_utc() + timedelta(days=5))
    r = join(client, h)
    assert r.status_code == 409 and code(r) == "WAITLIST_TOO_LATE"


def test_cancel_promotes_first_in_queue(client, h, book):
    b = book().json()
    first = join(client, h, who="s2").json()
    second = join(client, h, who="admin").json()
    assert second["position"] == 2

    assert client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s1"]).status_code == 200

    mine = client.get("/api/waitlist/me", headers=h["s2"]).json()[0]
    assert mine["id"] == first["id"] and mine["status"] == "PROMOTED" and mine["position"] is None
    booking = client.get(f"/api/bookings/{mine['booking_id']}", headers=h["s2"]).json()
    assert booking["status"] == "PENDING" and booking["booker_name"] == "สมหญิง รักเรียน"
    assert booking["logs"][0]["note"] == "สร้างจากคิวรอห้อง"
    assert "BOOKING_WAITLIST_PROMOTED" in [n["type"] for n in client.get("/api/notifications", headers=h["s2"]).json()]

    # the second person moved up, and still waits because the slot is held again
    other = client.get("/api/waitlist/me", headers=h["admin"]).json()[0]
    assert other["status"] == "WAITING" and other["position"] == 1


def test_reject_promotes(client, h, book):
    b = book().json()
    join(client, h)
    client.post(f"/api/bookings/{b['id']}/reject", headers=h["approver"], json={"reason": "x"})
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "PROMOTED"


def test_moving_a_booking_promotes(client, h, book):
    b = book().json()
    join(client, h)
    r = client.put(f"/api/bookings/{b['id']}", headers=h["s1"], json={
        "room_id": 1, "start_time": slot(1, 14), "end_time": slot(1, 15), "attendees": 5, "purpose": "ย้าย"})
    assert r.status_code == 200
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "PROMOTED"


def test_partial_release_does_not_promote(client, h, book):
    a = book(start=slot(1, 9), end=slot(1, 10)).json()
    book(who="admin", start=slot(1, 10), end=slot(1, 11))
    join(client, h, who="s2", start=slot(1, 9), end=slot(1, 11))  # wants 9-11, only 9-10 will free up
    client.post(f"/api/bookings/{a['id']}/cancel", headers=h["s1"])
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "WAITING"


def test_entry_that_does_not_fit_does_not_block_the_next(client, h, book):
    a = book(start=slot(1, 9), end=slot(1, 10)).json()
    book(who="admin", start=slot(1, 10), end=slot(1, 11))
    join(client, h, who="s2", start=slot(1, 9), end=slot(1, 11))        # first, can't fit
    join(client, h, who="approver", start=slot(1, 9), end=slot(1, 10))  # second, fits
    client.post(f"/api/bookings/{a['id']}/cancel", headers=h["s1"])
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "WAITING"
    assert client.get("/api/waitlist/me", headers=h["approver"]).json()[0]["status"] == "PROMOTED"


def test_leaving_the_queue(client, h, book):
    b = book().json()
    e = join(client, h).json()
    assert client.post(f"/api/waitlist/{e['id']}/cancel", headers=h["s1"]).status_code == 403
    r = client.post(f"/api/waitlist/{e['id']}/cancel", headers=h["s2"])
    assert r.status_code == 200 and r.json()["status"] == "CANCELLED"
    assert client.post(f"/api/waitlist/{e['id']}/cancel", headers=h["s2"]).status_code == 409
    client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s1"])
    assert client.get("/api/bookings/me", headers=h["s2"]).json() == []  # nothing created for someone who left


def test_rejoin_after_leaving(client, h, book):
    book()
    e = join(client, h).json()
    client.post(f"/api/waitlist/{e['id']}/cancel", headers=h["s2"])
    assert join(client, h).status_code == 201


def test_sweep_expires_entries_close_to_start(client, h, book, db):
    book()
    e = join(client, h).json()
    db.execute("UPDATE waitlist_entries SET start_time = now() + interval '30 min', end_time = now() + interval '90 min' "
               "WHERE id = %s", (e["id"],))
    sweep()
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "EXPIRED"
    assert "WAITLIST_EXPIRED" in [n["type"] for n in client.get("/api/notifications", headers=h["s2"]).json()]
    assert client.get("/api/bookings/me", headers=h["s2"]).json() == []


def test_no_show_promotes_entry_for_a_later_part_of_the_booking(client, h, db):
    db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose, status) "
               "VALUES (1, 3, now() - interval '20 min', now() + interval '5 hour', 5, 'x', 'APPROVED')")
    entry = db.execute(
        "INSERT INTO waitlist_entries (room_id, user_id, start_time, end_time, attendees, purpose) "
        "VALUES (1, 4, now() + interval '2 hour', now() + interval '3 hour', 5, 'y') RETURNING id").fetchone()[0]
    sweep()
    row = client.get("/api/waitlist/me", headers=h["s2"]).json()[0]
    assert row["id"] == entry and row["status"] == "PROMOTED"


def test_closure_blocks_promotion_until_removed(client, h, book):
    book()
    join(client, h)
    r = client.post("/api/admin/rooms/1/closures?cancel_conflicts=true", headers=h["admin"], json={
        "start_time": slot(1, 8), "end_time": slot(1, 12), "reason": "ซ่อม"})
    assert r.status_code == 201
    sweep()
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "WAITING"
    client.delete(f"/api/admin/closures/{r.json()['id']}", headers=h["admin"])
    sweep()
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "PROMOTED"


def test_direct_booking_that_beats_the_promotion_stays_consistent(client, h, book, db):
    b = book().json()
    join(client, h)
    db.execute("UPDATE bookings SET status = 'CANCELLED' WHERE id = %s", (b["id"],))
    assert book(who="admin").status_code == 201
    sweep()
    assert client.get("/api/waitlist/me", headers=h["s2"]).json()[0]["status"] == "WAITING"
    assert db.execute("SELECT count(*) FROM bookings WHERE status IN ('PENDING','APPROVED')").fetchone()[0] == 1
