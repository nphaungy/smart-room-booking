from datetime import date, datetime

import psycopg

from ..config import get_settings
from ..errors import ApiError
from ..models import ClosureIn, RoomIn
from ..rules import day_window, fmt_range, to_aware
from .notify import notify

ROOM_SELECT = """
    SELECT r.id, r.code, r.name, r.building, r.floor, r.capacity, r.type_id, t.name AS type_name,
           r.status, r.description, r.image_url
    FROM rooms r JOIN room_types t ON t.id = r.type_id
"""


def _attach_equipment(conn, rooms: list[dict]) -> list[dict]:
    if not rooms:
        return rooms
    rows = conn.execute(
        "SELECT re.room_id, e.id, e.name, re.quantity FROM room_equipment re "
        "JOIN equipment e ON e.id = re.equipment_id WHERE re.room_id = ANY(%s) ORDER BY e.name",
        ([r["id"] for r in rooms],),
    ).fetchall()
    by_room: dict[int, list] = {}
    for row in rows:
        by_room.setdefault(row["room_id"], []).append(
            {"id": row["id"], "name": row["name"], "quantity": row["quantity"]}
        )
    for r in rooms:
        r["equipment"] = by_room.get(r["id"], [])
    return rooms


# ------------------------------------------------------------------ RB02 / RB03
def search_rooms(conn: psycopg.Connection, *, start: datetime | None, end: datetime | None,
                 attendees: int | None, type_id: int | None, equipment_ids: list[int],
                 q: str | None, include_inactive: bool = False) -> list[dict]:
    if (start is None) != (end is None):
        raise ApiError(422, "INVALID_TIME_RANGE", "ต้องระบุทั้งเวลาเริ่มและเวลาสิ้นสุด")
    if start is not None:
        start, end = to_aware(start), to_aware(end)
        if not start < end:
            raise ApiError(422, "INVALID_TIME_RANGE", "เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด")
    params = {
        "inactive": include_inactive, "attendees": attendees, "type_id": type_id,
        "q": f"%{q.strip()}%" if q and q.strip() else None,
        "start": start, "end": end, "eq": equipment_ids, "n_eq": len(equipment_ids),
    }
    sql = ROOM_SELECT + """
        WHERE (%(inactive)s OR r.status = 'ACTIVE')
          AND (%(attendees)s::int IS NULL OR r.capacity >= %(attendees)s::int)
          AND (%(type_id)s::int IS NULL OR r.type_id = %(type_id)s::int)
          AND (%(q)s::text IS NULL OR r.name ILIKE %(q)s OR r.code ILIKE %(q)s OR r.building ILIKE %(q)s)
          AND (%(start)s::timestamptz IS NULL OR NOT EXISTS (
                SELECT 1 FROM bookings b
                WHERE b.room_id = r.id AND b.status IN ('PENDING', 'APPROVED')
                  AND tstzrange(b.start_time, b.end_time, '[)')
                      && tstzrange(%(start)s::timestamptz, %(end)s::timestamptz, '[)')))
          AND (%(start)s::timestamptz IS NULL OR NOT EXISTS (
                SELECT 1 FROM room_closures c
                WHERE c.room_id = r.id
                  AND c.start_time < %(end)s::timestamptz AND %(start)s::timestamptz < c.end_time))
          AND (%(n_eq)s = 0 OR (
                SELECT count(*) FROM room_equipment re
                WHERE re.room_id = r.id AND re.equipment_id = ANY(%(eq)s::int[])) = %(n_eq)s)
        ORDER BY r.building, r.code
    """
    return _attach_equipment(conn, conn.execute(sql, params).fetchall())


def get_room(conn, room_id: int) -> dict:
    row = conn.execute(ROOM_SELECT + " WHERE r.id = %s", (room_id,)).fetchone()
    if row is None:
        raise ApiError(404, "ROOM_NOT_FOUND", "ไม่พบห้องนี้")
    return _attach_equipment(conn, [row])[0]


# ------------------------------------------------------------------ RB04 schedule
def room_schedule(conn, room_id: int, d: date, viewer: dict) -> dict:
    get_room(conn, room_id)
    open_t, close_t = day_window(d)
    staff = viewer["role"] in ("APPROVER", "ADMIN")
    busy: list[dict] = []

    for b in conn.execute(
        "SELECT b.id, b.start_time, b.end_time, b.status, b.booker_id, b.purpose, u.full_name "
        "FROM bookings b JOIN users u ON u.id = b.booker_id "
        "WHERE b.room_id = %s AND b.status IN ('PENDING', 'APPROVED') "
        "AND b.start_time < %s AND %s < b.end_time ORDER BY b.start_time",
        (room_id, close_t, open_t),
    ).fetchall():
        mine = b["booker_id"] == viewer["id"]
        # Privacy: ordinary users only see that a slot is taken, not who booked it.
        label = f"{b['full_name']}: {b['purpose']}" if (staff or mine) else "ถูกจองแล้ว"
        busy.append({"start_time": b["start_time"], "end_time": b["end_time"], "kind": "BOOKING",
                     "status": b["status"], "booking_id": b["id"] if (staff or mine) else None,
                     "is_mine": mine, "label": label})

    for c in conn.execute(
        "SELECT start_time, end_time, reason FROM room_closures "
        "WHERE room_id = %s AND start_time < %s AND %s < end_time",
        (room_id, close_t, open_t),
    ).fetchall():
        busy.append({"start_time": max(c["start_time"], open_t), "end_time": min(c["end_time"], close_t),
                     "kind": "CLOSURE", "label": f"ปิดปรับปรุง: {c['reason']}"})

    busy.sort(key=lambda x: x["start_time"])

    # Free slots = gaps between the merged busy intervals inside opening hours
    free, cursor = [], open_t
    for item in busy:
        s = max(item["start_time"], open_t)
        if s > cursor:
            free.append({"start_time": cursor, "end_time": s})
        cursor = max(cursor, min(item["end_time"], close_t))
    if cursor < close_t:
        free.append({"start_time": cursor, "end_time": close_t})

    return {"room_id": room_id, "date": d.isoformat(), "open_time": open_t, "close_time": close_t,
            "slot_minutes": get_settings().slot_minutes, "busy": busy, "free": free}


# ------------------------------------------------------------------ RB10 admin: rooms
def _save_equipment(conn, room_id: int, data: RoomIn) -> None:
    conn.execute("DELETE FROM room_equipment WHERE room_id = %s", (room_id,))
    for item in data.equipment:
        conn.execute(
            "INSERT INTO room_equipment (room_id, equipment_id, quantity) VALUES (%s, %s, %s)",
            (room_id, item.equipment_id, item.quantity),
        )


def create_room(conn, data: RoomIn) -> dict:
    with conn.transaction():
        room_id = conn.execute(
            "INSERT INTO rooms (code, name, building, floor, capacity, type_id, status, description, image_url) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id",
            (data.code.strip(), data.name.strip(), data.building.strip(), data.floor, data.capacity,
             data.type_id, data.status, data.description, data.image_url),
        ).fetchone()["id"]
        _save_equipment(conn, room_id, data)
    return get_room(conn, room_id)


def update_room(conn, room_id: int, data: RoomIn) -> dict:
    with conn.transaction():
        if conn.execute("SELECT id FROM rooms WHERE id = %s FOR UPDATE", (room_id,)).fetchone() is None:
            raise ApiError(404, "ROOM_NOT_FOUND", "ไม่พบห้องนี้")
        conn.execute(
            "UPDATE rooms SET code = %s, name = %s, building = %s, floor = %s, capacity = %s, type_id = %s, "
            "status = %s, description = %s, image_url = %s WHERE id = %s",
            (data.code.strip(), data.name.strip(), data.building.strip(), data.floor, data.capacity,
             data.type_id, data.status, data.description, data.image_url, room_id),
        )
        _save_equipment(conn, room_id, data)
    return get_room(conn, room_id)


# ------------------------------------------------------------------ BR03 closures
def list_closures(conn, room_id: int) -> list[dict]:
    return conn.execute(
        "SELECT id, room_id, start_time, end_time, reason FROM room_closures "
        "WHERE room_id = %s AND end_time > now() ORDER BY start_time",
        (room_id,),
    ).fetchall()


def create_closure(conn, admin: dict, room_id: int, data: ClosureIn, cancel_conflicts: bool) -> dict:
    start, end = to_aware(data.start_time), to_aware(data.end_time)
    if not start < end:
        raise ApiError(422, "INVALID_TIME_RANGE", "เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด")
    with conn.transaction():
        # FOR UPDATE waits for in-flight bookings (which hold FOR SHARE) and blocks new ones,
        # so the conflict check below sees every booking that could overlap.
        room = conn.execute("SELECT id, code FROM rooms WHERE id = %s FOR UPDATE", (room_id,)).fetchone()
        if room is None:
            raise ApiError(404, "ROOM_NOT_FOUND", "ไม่พบห้องนี้")
        conflicts = conn.execute(
            "SELECT id, booker_id, status, start_time, end_time FROM bookings "
            "WHERE room_id = %s AND status IN ('PENDING', 'APPROVED') AND start_time < %s AND %s < end_time",
            (room_id, end, start),
        ).fetchall()
        if conflicts and not cancel_conflicts:
            raise ApiError(409, "CLOSURE_CONFLICT",
                           f"มีการจอง {len(conflicts)} รายการในช่วงนี้ ส่ง cancel_conflicts=true เพื่อยกเลิกอัตโนมัติ",
                           {"booking_ids": [c["id"] for c in conflicts]})
        for c in conflicts:
            conn.execute("UPDATE bookings SET status = 'CANCELLED', updated_at = now(), version = version + 1 "
                         "WHERE id = %s", (c["id"],))
            conn.execute("INSERT INTO booking_status_logs (booking_id, from_status, to_status, changed_by, note) "
                         "VALUES (%s, %s, 'CANCELLED', %s, %s)",
                         (c["id"], c["status"], admin["id"], f"ห้องปิดปรับปรุง: {data.reason}"))
            notify(conn, [c["booker_id"]], c["id"], "BOOKING_CANCELLED", "การจองถูกยกเลิกเนื่องจากห้องปิดปรับปรุง",
                   f"{room['code']} {fmt_range(c['start_time'], c['end_time'])} เหตุผล: {data.reason}")
        row = conn.execute(
            "INSERT INTO room_closures (room_id, start_time, end_time, reason, created_by) "
            "VALUES (%s, %s, %s, %s, %s) RETURNING id, room_id, start_time, end_time, reason",
            (room_id, start, end, data.reason.strip(), admin["id"]),
        ).fetchone()
    return row


def delete_closure(conn, closure_id: int) -> None:
    with conn.transaction():
        if conn.execute("DELETE FROM room_closures WHERE id = %s RETURNING id", (closure_id,)).fetchone() is None:
            raise ApiError(404, "CLOSURE_NOT_FOUND", "ไม่พบช่วงปิดปรับปรุงนี้")
