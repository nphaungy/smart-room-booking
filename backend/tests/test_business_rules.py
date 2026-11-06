"""BR01-BR03, BR07, BR08: rejected with clear error codes."""
import pytest
from psycopg import errors as pg

from .conftest import slot


def code(r):
    return r.json()["error"]["code"]


def test_br01_start_must_be_before_end(book):
    r = book(start=slot(1, 10), end=slot(1, 9))
    assert r.status_code == 422 and code(r) == "INVALID_TIME_RANGE"
    r = book(start=slot(1, 10), end=slot(1, 10))
    assert r.status_code == 422 and code(r) == "INVALID_TIME_RANGE"


def test_br01_enforced_by_database_too(db):
    with pytest.raises(pg.CheckViolation):
        db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
                   "VALUES (1, 3, now() + interval '2 day', now() + interval '1 day', 1, 'x')")


def test_br02_overlap_rejected_adjacent_allowed(book):
    assert book(start=slot(1, 9), end=slot(1, 10, 30)).status_code == 201
    # overlaps 10:00-10:30
    r = book(who="s2", start=slot(1, 10), end=slot(1, 11))
    assert r.status_code == 409 and code(r) == "BOOKING_CONFLICT"
    # fully inside
    assert book(who="s2", start=slot(1, 9, 30), end=slot(1, 10)).status_code == 409
    # fully covering
    assert book(who="s2", start=slot(1, 8), end=slot(1, 12)).status_code == 409
    # touches exactly at 10:30 -> allowed ('[)' range)
    assert book(who="s2", start=slot(1, 10, 30), end=slot(1, 11)).status_code == 201
    # same time, different room -> allowed
    assert book(who="s2", room_id=2, start=slot(1, 9), end=slot(1, 10)).status_code == 201


def test_br02_enforced_by_database_too(book, db):
    assert book().status_code == 201
    with pytest.raises(pg.ExclusionViolation):
        db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
                   "SELECT room_id, 4, start_time + interval '30 min', end_time + interval '30 min', 1, 'x' "
                   "FROM bookings LIMIT 1")


def test_br03_inactive_room(client, h, book, db):
    db.execute("UPDATE rooms SET status = 'INACTIVE' WHERE id = 1")
    r = book()
    assert r.status_code == 409 and code(r) == "ROOM_UNAVAILABLE"


def test_br03_closure_blocks_booking(client, h, book):
    r = client.post("/api/admin/rooms/1/closures", headers=h["admin"],
                    json={"start_time": slot(1, 8), "end_time": slot(1, 12), "reason": "ซ่อมแอร์"})
    assert r.status_code == 201, r.text
    r = book(start=slot(1, 11), end=slot(1, 13))
    assert r.status_code == 409 and code(r) == "ROOM_CLOSED"
    assert book(start=slot(1, 12), end=slot(1, 13)).status_code == 201


def test_br07_capacity(book):
    r = book(room_id=6, attendees=7)  # LIB-G1 holds 6
    assert r.status_code == 422 and code(r) == "OVER_CAPACITY"
    assert book(room_id=6, attendees=6).status_code == 201


@pytest.mark.parametrize("start,end,expected", [
    (slot(1, 9, 15), slot(1, 10), "INVALID_SLOT"),
    (slot(-1, 9), slot(-1, 10), "PAST_TIME"),
    (slot(45, 9), slot(45, 10), "TOO_FAR_AHEAD"),
    (slot(1, 7), slot(1, 9), "OUTSIDE_OPENING_HOURS"),
    (slot(1, 19), slot(1, 21), "OUTSIDE_OPENING_HOURS"),
])
def test_br08_time_policy(book, start, end, expected):
    r = book(start=start, end=end)
    assert r.status_code == 422 and code(r) == expected


def test_unknown_room(book):
    r = book(room_id=9999)
    assert r.status_code == 404 and code(r) == "ROOM_NOT_FOUND"
