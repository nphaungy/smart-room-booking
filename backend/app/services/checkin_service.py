"""QR check-in and the no-show record."""
import secrets

from psycopg import errors as pg

from ..config import get_settings
from ..errors import ApiError
from ..rules import checkin_window, fmt_range, now_utc
from .booking_service import _log, fetch_booking, get_booking
from .notify import notify


def _normalize(code: str) -> str:
    code = code.strip()
    # The app sends just the code, but a client may forward the whole QR content.
    if "code=" in code:
        code = code.split("code=", 1)[1]
    return code.strip().upper()


def room_code(conn, room_id: int) -> dict:
    row = conn.execute(
        "SELECT id AS room_id, code AS room_code, name AS room_name, checkin_code FROM rooms WHERE id = %s",
        (room_id,),
    ).fetchone()
    if row is None:
        raise ApiError(404, "ROOM_NOT_FOUND", "ไม่พบห้องนี้")
    return row


def rotate_room_code(conn, room_id: int) -> dict:
    room_code(conn, room_id)
    conn.execute("UPDATE rooms SET checkin_code = %s WHERE id = %s", (secrets.token_hex(5).upper(), room_id))
    return room_code(conn, room_id)


def check_in(conn, user: dict, booking_id: int, code: str) -> dict:
    with conn.transaction():
        b = fetch_booking(conn, booking_id, for_update=True)
        if b["booker_id"] != user["id"]:
            raise ApiError(403, "FORBIDDEN", "เช็กอินได้เฉพาะการจองของตนเอง")
        if b["checked_in_at"] is not None:
            raise ApiError(409, "ALREADY_CHECKED_IN", "เช็กอินการจองนี้แล้ว")
        if b["status"] == "NO_SHOW":
            raise ApiError(409, "CHECKIN_TOO_LATE", "เลยเวลาเช็กอินแล้ว กรุณาติดต่อเจ้าหน้าที่")
        if b["status"] != "APPROVED":
            raise ApiError(409, "INVALID_STATUS", "เช็กอินได้เฉพาะการจองที่อนุมัติแล้ว")

        opens, closes = checkin_window(b["start_time"])
        now = now_utc()
        if now < opens:
            raise ApiError(409, "CHECKIN_TOO_EARLY", f"ยังไม่ถึงเวลาเช็กอิน เช็กอินได้ {fmt_range(opens, closes)}")
        if now > closes:
            raise ApiError(409, "CHECKIN_TOO_LATE", "เลยเวลาเช็กอินแล้ว กรุณาติดต่อเจ้าหน้าที่")

        room = room_code(conn, b["room_id"])
        if not secrets.compare_digest(_normalize(code).encode(), room["checkin_code"].encode()):
            raise ApiError(422, "WRONG_ROOM_CODE", f"QR นี้ไม่ใช่ของห้อง {room['room_code']}")

        conn.execute(
            "UPDATE bookings SET checked_in_at = now(), checked_in_by = %s, checkin_method = 'SELF', "
            "updated_at = now(), version = version + 1 WHERE id = %s",
            (user["id"], booking_id),
        )
        _log(conn, booking_id, "APPROVED", "APPROVED", user["id"], "เช็กอินแล้ว")
    return get_booking(conn, booking_id, user)


def staff_check_in(conn, user: dict, booking_id: int, note: str) -> dict:
    note = note.strip()
    if not note:
        raise ApiError(422, "REASON_REQUIRED", "กรุณาระบุหมายเหตุ")
    try:
        with conn.transaction():
            b = fetch_booking(conn, booking_id, for_update=True)
            if b["checked_in_at"] is not None:
                raise ApiError(409, "ALREADY_CHECKED_IN", "เช็กอินการจองนี้แล้ว")
            if b["status"] not in ("APPROVED", "NO_SHOW"):
                raise ApiError(409, "INVALID_STATUS", "ยืนยันได้เฉพาะการจองที่อนุมัติแล้ว")
            opens, _ = checkin_window(b["start_time"])
            if now_utc() < opens:
                raise ApiError(409, "CHECKIN_TOO_EARLY", "ยังไม่ถึงเวลาเช็กอิน")
            conn.execute(
                "UPDATE bookings SET status = 'APPROVED', checked_in_at = now(), checked_in_by = %s, "
                "checkin_method = 'STAFF', updated_at = now(), version = version + 1 WHERE id = %s",
                (user["id"], booking_id),
            )
            _log(conn, booking_id, b["status"], "APPROVED", user["id"], f"เจ้าหน้าที่ยืนยันการมาใช้ห้อง: {note}")
            notify(conn, [b["booker_id"]], booking_id, "BOOKING_CHECKED_IN", "เจ้าหน้าที่ยืนยันการมาใช้ห้องแล้ว", note)
    except pg.ExclusionViolation:
        # A no-show frees the slot, so someone from the queue may have taken it since.
        raise ApiError(409, "SLOT_TAKEN", "แก้ไขไม่ได้ เพราะมีการจองอื่นในช่วงเวลานี้แล้ว ต้องยกเลิกการจองนั้นก่อน")
    return get_booking(conn, booking_id, user)


def mark_no_shows(conn) -> None:
    """Record every approved booking whose check-in deadline has passed."""
    grace = get_settings().checkin_grace_minutes
    with conn.transaction():
        # SKIP LOCKED: a check-in in progress keeps its row and is re-evaluated by the next sweep.
        rows = conn.execute(
            "SELECT b.id, b.booker_id, b.start_time, b.end_time, r.code AS room_code "
            "FROM bookings b JOIN rooms r ON r.id = b.room_id "
            "WHERE b.status = 'APPROVED' AND b.checkin_required AND b.checked_in_at IS NULL "
            "AND b.start_time + make_interval(mins => %s) < now() "
            "FOR UPDATE OF b SKIP LOCKED",
            (grace,),
        ).fetchall()
        for b in rows:
            conn.execute(
                "UPDATE bookings SET status = 'NO_SHOW', updated_at = now(), version = version + 1 WHERE id = %s",
                (b["id"],),
            )
            _log(conn, b["id"], "APPROVED", "NO_SHOW", None, f"ไม่เช็กอินภายใน {grace} นาทีหลังเวลาเริ่ม")
            notify(conn, [b["booker_id"]], b["id"], "BOOKING_NO_SHOW", "ถูกบันทึกว่าไม่มาใช้ห้อง",
                   f"{b['room_code']} {fmt_range(b['start_time'], b['end_time'])} "
                   "หากมาใช้ห้องจริง กรุณาติดต่อเจ้าหน้าที่")
