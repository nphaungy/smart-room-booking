"""RB02 room list, RB03 search, RB04 schedule (calendar)."""
from datetime import date, datetime

import psycopg
from fastapi import APIRouter, Depends, Query

from ..db import get_conn
from ..models import EquipmentOut, RoomOut, RoomTypeOut, ScheduleOut
from ..security import get_current_user
from ..services import room_service

router = APIRouter(tags=["Rooms (RB02-RB04)"])


@router.get("/rooms", response_model=list[RoomOut])
def list_rooms(
    start: datetime | None = Query(None, description="ISO datetime, e.g. 2026-11-10T09:00:00+07:00"),
    end: datetime | None = Query(None),
    attendees: int | None = Query(None, gt=0),
    type_id: int | None = None,
    equipment_ids: list[int] = Query(default=[]),
    q: str | None = Query(None, description="search by code, name or building"),
    user: dict = Depends(get_current_user),
    conn: psycopg.Connection = Depends(get_conn),
):
    """Without start/end: all active rooms (RB02). With start/end: only rooms free in that range (RB03)."""
    return room_service.search_rooms(
        conn, start=start, end=end, attendees=attendees, type_id=type_id,
        equipment_ids=equipment_ids, q=q, include_inactive=False,
    )


@router.get("/rooms/{room_id}", response_model=RoomOut)
def get_room(room_id: int, user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return room_service.get_room(conn, room_id)


@router.get("/rooms/{room_id}/schedule", response_model=ScheduleOut)
def schedule(room_id: int, date: date, user: dict = Depends(get_current_user),
             conn: psycopg.Connection = Depends(get_conn)):
    return room_service.room_schedule(conn, room_id, date, user)


@router.get("/room-types", response_model=list[RoomTypeOut])
def room_types(user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return conn.execute("SELECT id, name FROM room_types ORDER BY id").fetchall()


@router.get("/equipment", response_model=list[EquipmentOut])
def equipment(user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return conn.execute("SELECT id, name FROM equipment ORDER BY name").fetchall()
