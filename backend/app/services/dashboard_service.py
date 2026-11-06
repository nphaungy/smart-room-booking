"""RB09 dashboard: booking statistics for approvers and admins."""
from datetime import date, datetime, time, timedelta

from ..config import get_settings
from ..errors import ApiError


def dashboard(conn, date_from: date, date_to: date) -> dict:
    s = get_settings()
    if date_to < date_from:
        raise ApiError(422, "INVALID_DATE_RANGE", "วันที่สิ้นสุดต้องไม่น้อยกว่าวันที่เริ่ม")
    start = datetime.combine(date_from, time.min, tzinfo=s.tz)
    end = datetime.combine(date_to + timedelta(days=1), time.min, tzinfo=s.tz)
    days = (date_to - date_from).days + 1
    open_hours_per_room = days * (s.close_hour - s.open_hour)

    by_status = conn.execute(
        "SELECT status, count(*)::int AS count FROM bookings "
        "WHERE start_time >= %s AND start_time < %s GROUP BY status ORDER BY status",
        (start, end),
    ).fetchall()
    counts = {r["status"]: r["count"] for r in by_status}
    approved = counts.get("APPROVED", 0) + counts.get("NO_SHOW", 0)  # a no-show was still approved
    decided = approved + counts.get("REJECTED", 0)

    rooms = conn.execute(
        """
        SELECT r.id AS room_id, r.code AS room_code, r.name AS room_name,
               count(b.id)::int AS approved_bookings,
               COALESCE(sum(EXTRACT(EPOCH FROM (b.end_time - b.start_time)) / 3600.0), 0)::float AS approved_hours
        FROM rooms r
        LEFT JOIN bookings b ON b.room_id = r.id AND b.status = 'APPROVED'
             AND b.start_time >= %s AND b.start_time < %s
        WHERE r.status = 'ACTIVE'
        GROUP BY r.id ORDER BY approved_hours DESC, r.code
        """,
        (start, end),
    ).fetchall()
    for r in rooms:
        r["approved_hours"] = round(r["approved_hours"], 2)
        r["utilization_percent"] = round(100 * r["approved_hours"] / open_hours_per_room, 2)

    by_hour = conn.execute(
        "SELECT EXTRACT(HOUR FROM start_time AT TIME ZONE %s)::int AS hour, count(*)::int AS count "
        "FROM bookings WHERE status IN ('PENDING', 'APPROVED') AND start_time >= %s AND start_time < %s "
        "GROUP BY hour ORDER BY hour",
        (s.timezone, start, end),
    ).fetchall()

    pending_now = conn.execute(
        "SELECT count(*)::int AS n FROM bookings WHERE status = 'PENDING' AND start_time > now()"
    ).fetchone()["n"]

    return {
        "date_from": date_from.isoformat(),
        "date_to": date_to.isoformat(),
        "total_bookings": sum(counts.values()),
        "pending_now": pending_now,
        "by_status": by_status,
        "approval_rate_percent": round(100 * approved / decided, 2) if decided else None,
        "rooms": rooms,
        "by_hour": by_hour,
    }
