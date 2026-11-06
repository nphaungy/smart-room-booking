"""Booking workflow: create, edit, approve, reject, cancel.

State machine (RB06):
    PENDING --approve--> APPROVED
    PENDING --reject---> REJECTED   (reason required)
    PENDING/APPROVED --cancel--> CANCELLED
REJECTED and CANCELLED are final; they release the time slot because the
exclusion constraint only counts PENDING and APPROVED rows.
"""
import functools

import psycopg
from psycopg import errors as pg

from ..errors import ApiError
from ..models import BookingIn, BookingUpdateIn
from ..rules import fmt_range, now_utc, owner_can_cancel, validate_booking_time
from .notify import approver_ids, notify

BOOKING_SELECT = """
    SELECT b.*, r.code AS room_code, r.name AS room_name, r.building,
           u.full_name AS booker_name, a.full_name AS approver_name
    FROM bookings b
    JOIN rooms r ON r.id = b.room_id
    JOIN users u ON u.id = b.booker_id
    LEFT JOIN users a ON a.id = b.approver_id
"""

ACTIVE = ("PENDING", "APPROVED")


def retry_on_deadlock(fn):
    """Two transactions inserting *overlapping* ranges at the same instant each wait for the
    other while checking the exclusion constraint; PostgreSQL detects the cycle and aborts one
    (DeadlockDetected). Nothing was written, so we simply run it again: the retry then sees the
    other committed booking and fails cleanly with ExclusionViolation -> HTTP 409."""

    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        for attempt in range(5):
            try:
                return fn(*args, **kwargs)
            except (pg.DeadlockDetected, pg.SerializationFailure):
                if attempt == 4:
                    raise
    return wrapper


# ------------------------------------------------------------------ helpers
def decorate(row: dict, viewer: dict) -> dict:
    """Add can_edit / can_cancel / can_decide so the app knows which buttons to show.
    (The app only hides buttons; every action is re-checked on the server.)"""
    is_owner = row["booker_id"] == viewer["id"]
    is_admin = viewer["role"] == "ADMIN"
    future = row["start_time"] > now_utc()
    row["can_edit"] = is_owner and row["status"] == "PENDING" and future
    row["can_cancel"] = row["status"] in ACTIVE and future and (
        is_admin or (is_owner and owner_can_cancel(row["start_time"]))
    )
    row["can_decide"] = viewer["role"] in ("APPROVER", "ADMIN") and row["status"] == "PENDING" and future
    return row


def _log(conn, booking_id: int, from_s: str | None, to_s: str, by: int, note: str | None = None) -> None:
    conn.execute(
        "INSERT INTO booking_status_logs (booking_id, from_status, to_status, changed_by, note) "
        "VALUES (%s, %s, %s, %s, %s)",
        (booking_id, from_s, to_s, by, note),
    )


def _lock_room(conn, room_id: int, attendees: int) -> dict:
    # FOR SHARE: many bookings may proceed in parallel, but an admin who closes the room
    # (FOR UPDATE in room_service) must wait, so a closure can never slip in mid-booking.
    room = conn.execute("SELECT * FROM rooms WHERE id = %s FOR SHARE", (room_id,)).fetchone()
    if room is None:
        raise ApiError(404, "ROOM_NOT_FOUND", "ไม่พบห้องนี้")
    if room["status"] != "ACTIVE":  # BR03
        raise ApiError(409, "ROOM_UNAVAILABLE", "ห้องนี้ปิดใช้งานอยู่ ไม่สามารถจองได้")
    if attendees > room["capacity"]:  # BR07
        raise ApiError(422, "OVER_CAPACITY", f"จำนวนผู้เข้าร่วมเกินความจุห้อง ({room['capacity']} คน)")
    return room


def _check_closure(conn, room_id: int, start, end) -> None:
    closure = conn.execute(
        "SELECT reason, start_time, end_time FROM room_closures "
        "WHERE room_id = %s AND start_time < %s AND %s < end_time LIMIT 1",
        (room_id, end, start),
    ).fetchone()
    if closure:  # BR03
        raise ApiError(
            409, "ROOM_CLOSED",
            f"ห้องปิดปรับปรุงช่วง {fmt_range(closure['start_time'], closure['end_time'])}: {closure['reason']}",
        )


def fetch_booking(conn, booking_id: int, for_update: bool = False) -> dict:
    sql = "SELECT * FROM bookings WHERE id = %s" + (" FOR UPDATE" if for_update else "")
    row = conn.execute(sql, (booking_id,)).fetchone()
    if row is None:
        raise ApiError(404, "BOOKING_NOT_FOUND", "ไม่พบรายการจองนี้")
    return row


def get_booking(conn, booking_id: int, viewer: dict, with_logs: bool = False) -> dict:
    row = conn.execute(BOOKING_SELECT + " WHERE b.id = %s", (booking_id,)).fetchone()
    if row is None:
        raise ApiError(404, "BOOKING_NOT_FOUND", "ไม่พบรายการจองนี้")
    if viewer["role"] == "BOOKER" and row["booker_id"] != viewer["id"]:
        raise ApiError(403, "FORBIDDEN", "คุณไม่มีสิทธิ์ดูรายการนี้")
    row = decorate(row, viewer)
    if with_logs:
        row["logs"] = conn.execute(
            "SELECT l.from_status, l.to_status, u.full_name AS changed_by_name, l.changed_at, l.note "
            "FROM booking_status_logs l JOIN users u ON u.id = l.changed_by "
            "WHERE l.booking_id = %s ORDER BY l.id",
            (booking_id,),
        ).fetchall()
    return row


# ------------------------------------------------------------------ RB05 create
@retry_on_deadlock
def create_booking(conn: psycopg.Connection, user: dict, data: BookingIn) -> dict:
    start, end = validate_booking_time(data.start_time, data.end_time)  # BR01, BR08
    with conn.transaction():
        room = _lock_room(conn, data.room_id, data.attendees)            # BR03, BR07
        _check_closure(conn, data.room_id, start, end)                   # BR03
        # BR02 + BR05: if another active booking overlaps - even one committed a
        # millisecond ago by another user - PostgreSQL raises ExclusionViolation (-> HTTP 409).
        booking_id = conn.execute(
            "INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
            "VALUES (%s, %s, %s, %s, %s, %s) RETURNING id",
            (data.room_id, user["id"], start, end, data.attendees, data.purpose.strip()),
        ).fetchone()["id"]
        _log(conn, booking_id, None, "PENDING", user["id"], "สร้างคำขอจอง")
        notify(
            conn, approver_ids(conn), booking_id, "BOOKING_REQUESTED", "มีคำขอจองใหม่",
            f"{user['full_name']} ขอจอง {room['code']} {fmt_range(start, end)}",
        )
    return get_booking(conn, booking_id, user)


# ------------------------------------------------------------------ RB05 edit (BR10)
@retry_on_deadlock
def update_booking(conn: psycopg.Connection, user: dict, booking_id: int, data: BookingUpdateIn) -> dict:
    start, end = validate_booking_time(data.start_time, data.end_time)
    with conn.transaction():
        b = fetch_booking(conn, booking_id, for_update=True)
        if b["booker_id"] != user["id"]:
            raise ApiError(403, "FORBIDDEN", "แก้ไขได้เฉพาะคำขอของตนเอง")
        if b["status"] != "PENDING":
            raise ApiError(409, "INVALID_STATUS", "แก้ไขได้เฉพาะคำขอที่ยังรออนุมัติ (Pending)")
        if data.version is not None and data.version != b["version"]:
            raise ApiError(409, "STALE_VERSION", "รายการนี้ถูกแก้ไขไปแล้ว กรุณาโหลดข้อมูลใหม่")
        _lock_room(conn, data.room_id, data.attendees)
        _check_closure(conn, data.room_id, start, end)
        # The exclusion constraint ignores the row being updated, so moving a booking
        # to an overlapping time of ITSELF is fine, but onto someone else's is a 409.
        conn.execute(
            "UPDATE bookings SET room_id = %s, start_time = %s, end_time = %s, attendees = %s, "
            "purpose = %s, updated_at = now(), version = version + 1 WHERE id = %s",
            (data.room_id, start, end, data.attendees, data.purpose.strip(), booking_id),
        )
        _log(conn, booking_id, "PENDING", "PENDING", user["id"], f"แก้ไขเป็น {fmt_range(start, end)}")
    return get_booking(conn, booking_id, user)


# ------------------------------------------------------------------ RB07 approve / reject
def _decide(conn, user: dict, booking_id: int, approve: bool, reason: str | None) -> dict:
    with conn.transaction():
        b = fetch_booking(conn, booking_id, for_update=True)  # row lock: two approvers can't both decide
        if b["status"] != "PENDING":
            raise ApiError(409, "INVALID_STATUS", f"รายการนี้อยู่ในสถานะ {b['status']} แล้ว")
        if b["start_time"] <= now_utc():
            raise ApiError(409, "BOOKING_EXPIRED", "เลยเวลาเริ่มใช้ห้องแล้ว ไม่สามารถพิจารณาได้")
        new_status = "APPROVED" if approve else "REJECTED"
        conn.execute(
            "UPDATE bookings SET status = %s, approver_id = %s, reject_reason = %s, decided_at = now(), "
            "updated_at = now(), version = version + 1 WHERE id = %s",
            (new_status, user["id"], None if approve else reason.strip(), booking_id),
        )
        _log(conn, booking_id, "PENDING", new_status, user["id"], reason)
        room = conn.execute("SELECT code FROM rooms WHERE id = %s", (b["room_id"],)).fetchone()
        when = f"{room['code']} {fmt_range(b['start_time'], b['end_time'])}"
        if approve:
            notify(conn, [b["booker_id"]], booking_id, "BOOKING_APPROVED", "การจองได้รับอนุมัติ", when)
        else:
            notify(conn, [b["booker_id"]], booking_id, "BOOKING_REJECTED", "การจองถูกปฏิเสธ",
                   f"{when} เหตุผล: {reason.strip()}")
    return get_booking(conn, booking_id, user)


def approve_booking(conn, user: dict, booking_id: int) -> dict:
    return _decide(conn, user, booking_id, True, None)


def reject_booking(conn, user: dict, booking_id: int, reason: str) -> dict:
    if not reason.strip():  # BR09
        raise ApiError(422, "REASON_REQUIRED", "กรุณาระบุเหตุผลการปฏิเสธ")
    return _decide(conn, user, booking_id, False, reason)


# ------------------------------------------------------------------ RB05 cancel (BR04, BR06)
def cancel_booking(conn, user: dict, booking_id: int, note: str | None) -> dict:
    with conn.transaction():
        b = fetch_booking(conn, booking_id, for_update=True)
        is_owner = b["booker_id"] == user["id"]
        is_admin = user["role"] == "ADMIN"
        if not (is_owner or is_admin):  # BR04
            raise ApiError(403, "FORBIDDEN", "ยกเลิกได้เฉพาะคำขอของตนเอง")
        if b["status"] not in ACTIVE:
            raise ApiError(409, "INVALID_STATUS", f"รายการนี้อยู่ในสถานะ {b['status']} แล้ว")
        if b["start_time"] <= now_utc():
            raise ApiError(409, "BOOKING_STARTED", "เลยเวลาเริ่มใช้ห้องแล้ว ไม่สามารถยกเลิกได้")
        if not is_admin and not owner_can_cancel(b["start_time"]):  # BR06
            raise ApiError(409, "CANCEL_TOO_LATE", "ยกเลิกได้ก่อนเวลาเริ่มอย่างน้อย 1 ชั่วโมง")
        conn.execute(
            "UPDATE bookings SET status = 'CANCELLED', updated_at = now(), version = version + 1 WHERE id = %s",
            (booking_id,),
        )
        _log(conn, booking_id, b["status"], "CANCELLED", user["id"], note)
        room = conn.execute("SELECT code FROM rooms WHERE id = %s", (b["room_id"],)).fetchone()
        when = f"{room['code']} {fmt_range(b['start_time'], b['end_time'])}"
        if not is_owner:
            notify(conn, [b["booker_id"]], booking_id, "BOOKING_CANCELLED", "การจองถูกยกเลิกโดยผู้ดูแลระบบ",
                   f"{when}" + (f" หมายเหตุ: {note}" if note else ""))
        elif b["status"] == "APPROVED":
            notify(conn, approver_ids(conn), booking_id, "BOOKING_CANCELLED", "ผู้จองยกเลิกการจอง", when)
    return get_booking(conn, booking_id, user)
