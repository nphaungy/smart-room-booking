"""Monthly report for staff: usage, attendance, no-shows and waitlist activity."""
import calendar
import csv
import io
from datetime import date, datetime, time

from ..config import get_settings


def _month_range(year: int, month: int) -> tuple[datetime, datetime, int]:
    tz = get_settings().tz
    days = calendar.monthrange(year, month)[1]
    start = datetime.combine(date(year, month, 1), time.min, tzinfo=tz)
    next_first = date(year + 1, 1, 1) if month == 12 else date(year, month + 1, 1)
    return start, datetime.combine(next_first, time.min, tzinfo=tz), days


# A no-show was approved and held the room, so it counts towards booked but not used hours.
_HOURS = "EXTRACT(EPOCH FROM (b.end_time - b.start_time)) / 3600.0"
_BOOKED = "b.status IN ('APPROVED', 'NO_SHOW')"


def monthly_report(conn, year: int, month: int) -> dict:
    s = get_settings()
    start, end, days = _month_range(year, month)
    params = {"start": start, "end": end}

    summary = conn.execute(
        f"""
        SELECT count(*)::int AS total_requests,
               count(*) FILTER (WHERE {_BOOKED})::int AS approved,
               count(*) FILTER (WHERE b.status = 'PENDING')::int AS pending,
               count(*) FILTER (WHERE b.status = 'REJECTED')::int AS rejected,
               count(*) FILTER (WHERE b.status = 'CANCELLED')::int AS cancelled,
               count(*) FILTER (WHERE b.checked_in_at IS NOT NULL)::int AS checked_in,
               count(*) FILTER (WHERE b.status = 'NO_SHOW')::int AS no_shows,
               COALESCE(sum({_HOURS}) FILTER (WHERE {_BOOKED}), 0)::float AS booked_hours,
               COALESCE(sum({_HOURS}) FILTER (WHERE b.checked_in_at IS NOT NULL), 0)::float AS used_hours
        FROM bookings b WHERE b.start_time >= %(start)s AND b.start_time < %(end)s
        """,
        params,
    ).fetchone()
    attended = summary["checked_in"] + summary["no_shows"]
    summary["attendance_rate_percent"] = round(100 * summary["checked_in"] / attended, 2) if attended else None
    summary["booked_hours"] = round(summary["booked_hours"], 2)
    summary["used_hours"] = round(summary["used_hours"], 2)

    # An inactive room stays in the report if it was used this month.
    rooms = conn.execute(
        f"""
        SELECT r.id AS room_id, r.code AS room_code, r.name AS room_name,
               count(b.id) FILTER (WHERE {_BOOKED})::int AS approved,
               count(b.id) FILTER (WHERE b.checked_in_at IS NOT NULL)::int AS checked_in,
               count(b.id) FILTER (WHERE b.status = 'NO_SHOW')::int AS no_shows,
               COALESCE(sum({_HOURS}) FILTER (WHERE {_BOOKED}), 0)::float AS booked_hours,
               COALESCE(sum({_HOURS}) FILTER (WHERE b.checked_in_at IS NOT NULL), 0)::float AS used_hours
        FROM rooms r
        LEFT JOIN bookings b ON b.room_id = r.id AND b.start_time >= %(start)s AND b.start_time < %(end)s
        GROUP BY r.id
        HAVING r.status = 'ACTIVE' OR count(b.id) FILTER (WHERE {_BOOKED}) > 0
        ORDER BY booked_hours DESC, r.code
        """,
        params,
    ).fetchall()
    open_hours = days * (s.close_hour - s.open_hour)
    for r in rooms:
        r["utilization_percent"] = round(100 * r["booked_hours"] / open_hours, 2)
        r["booked_hours"] = round(r["booked_hours"], 2)
        r["used_hours"] = round(r["used_hours"], 2)

    no_show_users = conn.execute(
        """
        SELECT u.id AS user_id, u.full_name, u.email, u.faculty, count(*)::int AS no_shows
        FROM bookings b JOIN users u ON u.id = b.booker_id
        WHERE b.status = 'NO_SHOW' AND b.start_time >= %(start)s AND b.start_time < %(end)s
        GROUP BY u.id ORDER BY no_shows DESC, u.full_name LIMIT 50
        """,
        params,
    ).fetchall()

    waitlist = conn.execute(
        """
        SELECT count(*)::int AS joined,
               count(*) FILTER (WHERE status = 'WAITING')::int AS waiting,
               count(*) FILTER (WHERE status = 'PROMOTED')::int AS promoted,
               count(*) FILTER (WHERE status = 'CANCELLED')::int AS cancelled,
               count(*) FILTER (WHERE status = 'EXPIRED')::int AS expired
        FROM waitlist_entries WHERE start_time >= %(start)s AND start_time < %(end)s
        """,
        params,
    ).fetchone()

    return {"year": year, "month": month, "summary": summary, "rooms": rooms,
            "no_show_users": no_show_users, "waitlist": waitlist}


def _cell(value):
    # Names are typed by users; a leading = + - @ would run as a formula when opened in Excel.
    if isinstance(value, str) and value[:1] in ("=", "+", "-", "@", "\t", "\r"):
        return "'" + value
    return "-" if value is None else value


def report_csv(report: dict) -> bytes:
    out = io.StringIO()
    w = csv.writer(out)

    def row(*cells):
        w.writerow([_cell(c) for c in cells])

    s = report["summary"]
    row(f"รายงานการใช้ห้องประจำเดือน {report['month']:02d}/{report['year']}")
    w.writerow([])
    row("สรุป")
    for label, key in [("คำขอทั้งหมด", "total_requests"), ("อนุมัติ", "approved"), ("รออนุมัติ", "pending"),
                       ("ปฏิเสธ", "rejected"), ("ยกเลิก", "cancelled"), ("เช็กอิน", "checked_in"),
                       ("ไม่มาใช้ห้อง", "no_shows"), ("อัตราการมาใช้ห้อง (%)", "attendance_rate_percent"),
                       ("ชั่วโมงที่จอง", "booked_hours"), ("ชั่วโมงที่ใช้จริง", "used_hours")]:
        row(label, s[key])
    w.writerow([])
    row("แยกตามห้อง")
    row("รหัสห้อง", "ชื่อห้อง", "อนุมัติ", "เช็กอิน", "ไม่มาใช้ห้อง", "ชั่วโมงที่จอง", "ชั่วโมงที่ใช้จริง", "อัตราการใช้ห้อง (%)")
    for r in report["rooms"]:
        row(r["room_code"], r["room_name"], r["approved"], r["checked_in"], r["no_shows"],
            r["booked_hours"], r["used_hours"], r["utilization_percent"])
    w.writerow([])
    row("ผู้ที่ไม่มาใช้ห้อง")
    row("ชื่อ", "อีเมล", "คณะ", "จำนวนครั้ง")
    for u in report["no_show_users"]:
        row(u["full_name"], u["email"], u["faculty"], u["no_shows"])
    w.writerow([])
    q = report["waitlist"]
    row("คิวรอห้อง")
    for label, key in [("เข้าคิว", "joined"), ("กำลังรอ", "waiting"), ("ได้ห้อง", "promoted"),
                       ("ออกจากคิว", "cancelled"), ("หมดเวลา", "expired")]:
        row(label, q[key])
    # BOM: without it Excel reads the file as ANSI and the Thai text turns to garbage.
    return out.getvalue().encode("utf-8-sig")
