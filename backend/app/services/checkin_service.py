"""E-ticket check-in and the no-show record.

Every booking has its own ticket code. Once the booking is approved, the booker's app shows it as a QR
(like a cinema or concert e-ticket); an approver or admin scans it at the room to check the booker in.
"""
import re

from psycopg import errors as pg

from ..config import get_settings
from ..errors import ApiError
from ..rules import checkin_window, fmt_range, now_utc
from .booking_service import _log, fetch_booking, get_booking
from .notify import notify

TICKET_PREFIX = "roombooking://ticket/"
_TICKET_FORMAT = re.compile(r"^[0-9A-F]{12}$")


def normalize_ticket(raw: str) -> str:
    """Accepts the full QR content or the code typed by hand, in any case and with stray spaces."""
    code = raw.strip()
    if code.lower().startswith(TICKET_PREFIX):
        code = code[len(TICKET_PREFIX):]
    return code.replace(" ", "").replace("-", "").upper()


def scan_ticket(conn, staff: dict, raw: str) -> dict:
    """Staff scan a booker's e-ticket. Returns the booking so the scanner can show who and which room."""
    code = normalize_ticket(raw)
    if not _TICKET_FORMAT.match(code):
        raise ApiError(422, "INVALID_TICKET", "QR นี้ไม่ใช่ตั๋วเช็กอินของระบบจองห้อง")
    found = conn.execute("SELECT id FROM bookings WHERE ticket_code = %s", (code,)).fetchone()
    if found is None:
        raise ApiError(404, "TICKET_NOT_FOUND", "ไม่พบการจองของ QR นี้")
    booking_id = found["id"]

    with conn.transaction():
        b = fetch_booking(conn, booking_id, for_update=True)
        room = conn.execute("SELECT code FROM rooms WHERE id = %s", (b["room_id"],)).fetchone()
        what = f"{room['code']} {fmt_range(b['start_time'], b['end_time'])}"
        if b["checked_in_at"] is not None:
            raise ApiError(409, "ALREADY_CHECKED_IN", f"ตั๋วนี้เช็กอินไปแล้ว ({what})")
        if b["status"] == "NO_SHOW":
            raise ApiError(409, "CHECKIN_TOO_LATE",
                           f"เลยเวลาเช็กอินแล้ว ระบบบันทึกว่าไม่มาใช้ห้อง ({what}) "
                           "ถ้ามาใช้จริง ให้กด \"ยืนยันว่ามาใช้ห้อง\" ในรายละเอียดการจอง")
        if b["status"] != "APPROVED":
            raise ApiError(409, "INVALID_STATUS", f"การจองนี้ไม่ได้อยู่ในสถานะอนุมัติ ({what})")

        opens, closes = checkin_window(b["start_time"])
        now = now_utc()
        if now < opens:
            raise ApiError(409, "CHECKIN_TOO_EARLY", f"ยังไม่ถึงเวลาเช็กอินของ {what} เช็กอินได้ {fmt_range(opens, closes)}")
        if now > closes:
            raise ApiError(409, "CHECKIN_TOO_LATE", f"เลยเวลาเช็กอินของ {what} แล้ว")

        conn.execute(
            "UPDATE bookings SET checked_in_at = now(), checked_in_by = %s, checkin_method = 'SCAN', "
            "updated_at = now(), version = version + 1 WHERE id = %s",
            (staff["id"], booking_id),
        )
        _log(conn, booking_id, "APPROVED", "APPROVED", staff["id"], "เจ้าหน้าที่สแกน QR เช็กอิน")
        notify(conn, [b["booker_id"]], booking_id, "BOOKING_CHECKED_IN", "เช็กอินเรียบร้อย", what)
    return get_booking(conn, booking_id, staff)


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
