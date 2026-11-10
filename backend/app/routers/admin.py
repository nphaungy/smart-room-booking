"""RB10: admin manages rooms, equipment, room types, closures and user roles."""
import psycopg
from fastapi import APIRouter, Depends, Query, Response

from ..db import get_conn
from ..errors import ApiError
from ..models import (ClosureIn, ClosureOut, EquipmentOut, NameIn, RoleUpdateIn, RoomIn, RoomOut,
                      RoomTypeOut, UserOut)
from ..security import require_roles
from ..services import room_service

router = APIRouter(prefix="/admin", tags=["Admin (RB10)"])
admin = require_roles("ADMIN")


@router.get("/rooms", response_model=list[RoomOut])
def all_rooms(user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return room_service.search_rooms(conn, start=None, end=None, attendees=None, type_id=None,
                                     equipment_ids=[], q=None, include_inactive=True)


@router.post("/rooms", response_model=RoomOut, status_code=201)
def create_room(data: RoomIn, user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return room_service.create_room(conn, data)


@router.put("/rooms/{room_id}", response_model=RoomOut)
def update_room(room_id: int, data: RoomIn, user: dict = Depends(admin),
                conn: psycopg.Connection = Depends(get_conn)):
    """Set status=INACTIVE to take a room out of service (rooms are never hard-deleted,
    because past bookings still reference them)."""
    return room_service.update_room(conn, room_id, data)


@router.get("/rooms/{room_id}/closures", response_model=list[ClosureOut])
def closures(room_id: int, user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return room_service.list_closures(conn, room_id)


@router.post("/rooms/{room_id}/closures", response_model=ClosureOut, status_code=201)
def add_closure(room_id: int, data: ClosureIn, cancel_conflicts: bool = Query(False),
                user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return room_service.create_closure(conn, user, room_id, data, cancel_conflicts)


@router.delete("/closures/{closure_id}", status_code=204)
def remove_closure(closure_id: int, user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    room_service.delete_closure(conn, closure_id)
    return Response(status_code=204)


@router.post("/equipment", response_model=EquipmentOut, status_code=201)
def add_equipment(data: NameIn, user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return conn.execute("INSERT INTO equipment (name) VALUES (%s) RETURNING id, name", (data.name.strip(),)).fetchone()


@router.put("/equipment/{equipment_id}", response_model=EquipmentOut)
def rename_equipment(equipment_id: int, data: NameIn, user: dict = Depends(admin),
                     conn: psycopg.Connection = Depends(get_conn)):
    row = conn.execute("UPDATE equipment SET name = %s WHERE id = %s RETURNING id, name",
                       (data.name.strip(), equipment_id)).fetchone()
    if row is None:
        raise ApiError(404, "EQUIPMENT_NOT_FOUND", "ไม่พบอุปกรณ์นี้")
    return row


@router.delete("/equipment/{equipment_id}", status_code=204)
def delete_equipment(equipment_id: int, user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    if conn.execute("DELETE FROM equipment WHERE id = %s RETURNING id", (equipment_id,)).fetchone() is None:
        raise ApiError(404, "EQUIPMENT_NOT_FOUND", "ไม่พบอุปกรณ์นี้")
    return Response(status_code=204)


@router.post("/room-types", response_model=RoomTypeOut, status_code=201)
def add_room_type(data: NameIn, user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return conn.execute("INSERT INTO room_types (name) VALUES (%s) RETURNING id, name", (data.name.strip(),)).fetchone()


@router.get("/users", response_model=list[UserOut])
def users(user: dict = Depends(admin), conn: psycopg.Connection = Depends(get_conn)):
    return conn.execute("SELECT id, email, full_name, role, faculty FROM users ORDER BY id").fetchall()


@router.patch("/users/{user_id}/role", response_model=UserOut)
def set_role(user_id: int, data: RoleUpdateIn, user: dict = Depends(admin),
             conn: psycopg.Connection = Depends(get_conn)):
    if user_id == user["id"] and data.role != "ADMIN":
        raise ApiError(409, "CANNOT_DEMOTE_SELF", "ไม่สามารถลดสิทธิ์ของตนเองได้")
    row = conn.execute("UPDATE users SET role = %s WHERE id = %s RETURNING id, email, full_name, role, faculty",
                       (data.role, user_id)).fetchone()
    if row is None:
        raise ApiError(404, "USER_NOT_FOUND", "ไม่พบผู้ใช้")
    return row
