"""Waitlist: queue for a taken time, and get a PENDING booking automatically when it frees up.

The booking still goes through normal approval. Promotion stops one cancellation window before
the start, because the owner could otherwise be handed a booking they aren't allowed to cancel
and then be recorded as a no-show for it.
"""
import logging
from datetime import timedelta

import psycopg
from psycopg import errors as pg

from ..config import get_settings
from ..errors import ApiError
from ..models import BookingIn
from ..rules import fmt_range, now_utc, validate_booking_time
from .booking_service import _check_closure, _lock_room, _log, retry_on_deadlock
from .notify import approver_ids, notify

log = logging.getLogger(__name__)

ENTRY_SELECT = """
    SELECT w.id, w.room_id, r.code AS room_code, r.name AS room_name, r.building,
           w.start_time, w.end_time, w.attendees, w.purpose, w.status, w.booking_id, w.created_at,
           CASE WHEN w.status = 'WAITING' THEN (
               SELECT count(*)::int + 1 FROM waitlist_entries o
               WHERE o.room_id = w.room_id AND o.status = 'WAITING'
                 AND tstzrange(o.start_time, o.end_time, '[)') && tstzrange(w.start_time, w.end_time, '[)')
                 AND (o.created_at, o.id) < (w.created_at, w.id)
           ) END AS position
    FROM waitlist_entries w JOIN rooms r ON r.id = w.room_id
"""


def _cutoff():
    """Entries starting before this moment can no longer be promoted."""
    return now_utc() + timedelta(hours=get_settings().cancel_min_hours_before)


def _get_entry(conn, entry_id: int) -> dict:
    return conn.execute(ENTRY_SELECT + " WHERE w.id = %s", (entry_id,)).fetchone()


@retry_on_deadlock
def join(conn: psycopg.Connection, user: dict, data: BookingIn) -> dict:
    start, end = validate_booking_time(data.start_time, data.end_time)
    if start <= _cutoff():
        raise ApiError(409, "WAITLIST_TOO_LATE",
                       f"เข้าคิวได้ก่อนเวลาเริ่มอย่างน้อย {get_settings().cancel_min_hours_before} ชั่วโมง")
    try:
        with conn.transaction():
            _lock_room(conn, data.room_id, data.attendees)
            _check_closure(conn, data.room_id, start, end)
            holders = conn.execute(
                "SELECT booker_id FROM bookings WHERE room_id = %s AND status IN ('PENDING', 'APPROVED') "
                "AND start_time < %s AND %s < end_time",
                (data.room_id, end, start),
            ).fetchall()
            if not holders:
                raise ApiError(409, "SLOT_AVAILABLE", "ช่วงเวลานี้ว่างอยู่ สามารถจองได้เลย")
            if any(h["booker_id"] == user["id"] for h in holders):
                raise ApiError(409, "ALREADY_BOOKED", "คุณมีการจองในช่วงเวลานี้อยู่แล้ว")
            entry_id = conn.execute(
                "INSERT INTO waitlist_entries (room_id, user_id, start_time, end_time, attendees, purpose) "
                "VALUES (%s, %s, %s, %s, %s, %s) RETURNING id",
                (data.room_id, user["id"], start, end, data.attendees, data.purpose.strip()),
            ).fetchone()["id"]
    except pg.ExclusionViolation:
        raise ApiError(409, "ALREADY_IN_WAITLIST", "คุณอยู่ในคิวของห้องนี้ในช่วงเวลานี้แล้ว")
    return _get_entry(conn, entry_id)


def list_mine(conn, user: dict) -> list[dict]:
    return conn.execute(
        ENTRY_SELECT + " WHERE w.user_id = %s ORDER BY (w.status = 'WAITING') DESC, w.start_time DESC LIMIT 100",
        (user["id"],),
    ).fetchall()


def cancel(conn, user: dict, entry_id: int) -> dict:
    with conn.transaction():
        row = conn.execute("SELECT user_id, status FROM waitlist_entries WHERE id = %s FOR UPDATE",
                           (entry_id,)).fetchone()
        if row is None:
            raise ApiError(404, "WAITLIST_NOT_FOUND", "ไม่พบรายการคิวนี้")
        if row["user_id"] != user["id"]:
            raise ApiError(403, "FORBIDDEN", "ออกจากคิวได้เฉพาะรายการของตนเอง")
        if row["status"] != "WAITING":
            raise ApiError(409, "INVALID_STATUS", "รายการนี้ไม่ได้อยู่ในคิวแล้ว")
        conn.execute("UPDATE waitlist_entries SET status = 'CANCELLED', updated_at = now() WHERE id = %s", (entry_id,))
    return _get_entry(conn, entry_id)


def promote_room(conn, room_id: int) -> int:
    """Turn the oldest entries that now fit into PENDING bookings. Returns how many were promoted."""
    promoted = 0
    with conn.transaction():
        # Two promoters for one room would skip each other's rows and could break queue order.
        conn.execute("SELECT pg_advisory_xact_lock(%s)", (room_id,))
        entries = conn.execute(
            "SELECT w.*, u.full_name FROM waitlist_entries w JOIN users u ON u.id = w.user_id "
            "WHERE w.room_id = %s AND w.status = 'WAITING' AND w.start_time > %s "
            "ORDER BY w.created_at, w.id FOR UPDATE OF w",
            (room_id, _cutoff()),
        ).fetchall()
        for e in entries:
            try:
                with conn.transaction():  # savepoint: an entry that doesn't fit leaves the rest intact
                    room = _lock_room(conn, room_id, e["attendees"])
                    _check_closure(conn, room_id, e["start_time"], e["end_time"])
                    booking_id = conn.execute(
                        "INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
                        "VALUES (%s, %s, %s, %s, %s, %s) RETURNING id",
                        (room_id, e["user_id"], e["start_time"], e["end_time"], e["attendees"], e["purpose"]),
                    ).fetchone()["id"]
            except (ApiError, pg.ExclusionViolation):
                continue
            conn.execute(
                "UPDATE waitlist_entries SET status = 'PROMOTED', booking_id = %s, updated_at = now() WHERE id = %s",
                (booking_id, e["id"]),
            )
            _log(conn, booking_id, None, "PENDING", e["user_id"], "สร้างจากคิวรอห้อง")
            when = f"{room['code']} {fmt_range(e['start_time'], e['end_time'])}"
            notify(conn, [e["user_id"]], booking_id, "BOOKING_WAITLIST_PROMOTED", "ถึงคิวของคุณแล้ว",
                   f"{when} สร้างคำขอจองให้แล้ว กำลังรออนุมัติ")
            notify(conn, approver_ids(conn), booking_id, "BOOKING_REQUESTED", "มีคำขอจองใหม่",
                   f"{e['full_name']} ขอจอง {when} (จากคิวรอห้อง)")
            promoted += 1
    return promoted


def promote_quietly(conn, room_id: int) -> None:
    """After a slot is released. The release already succeeded, so a failure here must not fail the
    request; the next sweep tries again."""
    try:
        promote_room(conn, room_id)
    except psycopg.Error:
        log.exception("Waitlist promotion failed for room %s", room_id)


def expire_entries(conn) -> None:
    with conn.transaction():
        rows = conn.execute(
            "UPDATE waitlist_entries w SET status = 'EXPIRED', updated_at = now() FROM rooms r "
            "WHERE r.id = w.room_id AND w.status = 'WAITING' AND w.start_time <= %s "
            "RETURNING w.user_id, w.start_time, w.end_time, r.code AS room_code",
            (_cutoff(),),
        ).fetchall()
        for r in rows:
            notify(conn, [r["user_id"]], None, "WAITLIST_EXPIRED", "คิวรอห้องหมดเวลา",
                   f"{r['room_code']} {fmt_range(r['start_time'], r['end_time'])} ห้องไม่ว่างก่อนถึงเวลา")


def rooms_with_waiting(conn) -> set[int]:
    rows = conn.execute("SELECT DISTINCT room_id FROM waitlist_entries WHERE status = 'WAITING'").fetchall()
    return {r["room_id"] for r in rows}
