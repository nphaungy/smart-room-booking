"""Business rules that are checked in Python before touching the database.

The database repeats the most important ones (BR01, BR02/BR05, BR09) as
constraints, so even a bug here cannot produce a double booking.
"""
from datetime import date, datetime, time, timedelta, timezone

from .config import get_settings
from .errors import ApiError


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def to_aware(dt: datetime) -> datetime:
    """A datetime without a UTC offset is interpreted as local time (Asia/Bangkok)."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=get_settings().tz)
    return dt


def local(dt: datetime) -> datetime:
    return to_aware(dt).astimezone(get_settings().tz)


def fmt_range(start: datetime, end: datetime) -> str:
    s, e = local(start), local(end)
    return f"{s:%d/%m/%Y} {s:%H:%M}–{e:%H:%M}"


def day_window(d: date) -> tuple[datetime, datetime]:
    s = get_settings()
    return (
        datetime.combine(d, time(s.open_hour), tzinfo=s.tz),
        datetime.combine(d, time(s.close_hour), tzinfo=s.tz),
    )


def validate_booking_time(start: datetime, end: datetime) -> tuple[datetime, datetime]:
    s = get_settings()
    start, end = to_aware(start), to_aware(end)

    # BR01: start must be before end
    if not start < end:
        raise ApiError(422, "INVALID_TIME_RANGE", "เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด")

    ls, le = local(start), local(end)

    # BR08: whole slot units (e.g. 09:00, 09:30)
    for t in (ls, le):
        if t.minute % s.slot_minutes or t.second or t.microsecond:
            raise ApiError(422, "INVALID_SLOT", f"เวลาต้องเป็นช่วงละ {s.slot_minutes} นาที เช่น 09:00 หรือ 09:30")

    # BR08: no booking in the past, no booking too far ahead
    now = now_utc()
    if start <= now:
        raise ApiError(422, "PAST_TIME", "ไม่สามารถจองย้อนหลังหรือเวลาที่ผ่านมาแล้ว")
    if start > now + timedelta(days=s.max_advance_days):
        raise ApiError(422, "TOO_FAR_AHEAD", f"จองล่วงหน้าได้ไม่เกิน {s.max_advance_days} วัน")

    # BR08: within opening hours on one day
    open_t, close_t = day_window(ls.date())
    if le.date() != ls.date() or ls < open_t or le > close_t:
        raise ApiError(
            422,
            "OUTSIDE_OPENING_HOURS",
            f"จองได้เฉพาะ {s.open_hour:02d}:00–{s.close_hour:02d}:00 ภายในวันเดียวกัน",
        )
    return start, end


def checkin_window(start_time: datetime) -> tuple[datetime, datetime]:
    s = get_settings()
    return (start_time - timedelta(minutes=s.checkin_open_minutes_before),
            start_time + timedelta(minutes=s.checkin_grace_minutes))


def owner_can_cancel(start_time: datetime) -> bool:
    """BR06: the booker may cancel up to N hours before the start time."""
    return now_utc() <= start_time - timedelta(hours=get_settings().cancel_min_hours_before)
