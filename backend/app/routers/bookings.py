"""RB05 create/edit/cancel, RB06 statuses, RB07 approve/reject, RB09 history."""
from datetime import date, datetime, time, timedelta

import psycopg
from fastapi import APIRouter, Depends, Query

from ..config import get_settings
from ..db import get_conn
from ..models import (BookingDetailOut, BookingIn, BookingOut, BookingStatus, BookingUpdateIn, CancelIn,
                      RejectIn, StaffCheckInIn, TicketScanIn)
from ..security import get_current_user, require_roles
from ..services import booking_service as svc
from ..services import checkin_service
from ..services.waitlist_service import promote_quietly

router = APIRouter(tags=["Bookings (RB05-RB07, RB09)"])
staff = require_roles("APPROVER", "ADMIN")


@router.post("/bookings", response_model=BookingOut, status_code=201)
def create(data: BookingIn, user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return svc.create_booking(conn, user, data)


@router.get("/bookings/me", response_model=list[BookingOut])
def my_bookings(
    status: BookingStatus | None = None,
    upcoming: bool = False,
    user: dict = Depends(get_current_user),
    conn: psycopg.Connection = Depends(get_conn),
):
    rows = conn.execute(
        svc.BOOKING_SELECT + " WHERE b.booker_id = %s AND (%s::booking_status IS NULL OR b.status = %s::booking_status)"
        " AND (NOT %s OR b.end_time > now()) ORDER BY b.start_time DESC LIMIT 200",
        (user["id"], status, status, upcoming),
    ).fetchall()
    return [svc.decorate(r, user) for r in rows]


@router.get("/bookings", response_model=list[BookingOut])
def all_bookings(
    status: BookingStatus | None = None,
    room_id: int | None = None,
    date_from: date | None = None,
    date_to: date | None = None,
    user: dict = Depends(staff),
    conn: psycopg.Connection = Depends(get_conn),
):
    """Approvers/admins: every booking, filterable. Pending queue = ?status=PENDING (oldest first)."""
    tz = get_settings().tz
    start = datetime.combine(date_from, time.min, tzinfo=tz) if date_from else None
    end = datetime.combine(date_to + timedelta(days=1), time.min, tzinfo=tz) if date_to else None
    order = "b.created_at ASC" if status == "PENDING" else "b.start_time DESC"
    rows = conn.execute(
        svc.BOOKING_SELECT
        + " WHERE (%(s)s::booking_status IS NULL OR b.status = %(s)s::booking_status)"
          " AND (%(r)s::bigint IS NULL OR b.room_id = %(r)s::bigint)"
          " AND (%(a)s::timestamptz IS NULL OR b.start_time >= %(a)s::timestamptz)"
          " AND (%(b)s::timestamptz IS NULL OR b.start_time < %(b)s::timestamptz)"
        + f" ORDER BY {order} LIMIT 500",
        {"s": status, "r": room_id, "a": start, "b": end},
    ).fetchall()
    return [svc.decorate(r, user) for r in rows]


@router.get("/bookings/{booking_id}", response_model=BookingDetailOut)
def detail(booking_id: int, user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return svc.get_booking(conn, booking_id, user, with_logs=True)


@router.put("/bookings/{booking_id}", response_model=BookingOut)
def edit(booking_id: int, data: BookingUpdateIn, user: dict = Depends(get_current_user),
         conn: psycopg.Connection = Depends(get_conn)):
    booking = svc.update_booking(conn, user, booking_id, data)
    promote_quietly(conn, booking["room_id"])  # the old time may now be free
    return booking


@router.post("/bookings/{booking_id}/cancel", response_model=BookingOut)
def cancel(booking_id: int, data: CancelIn | None = None, user: dict = Depends(get_current_user),
           conn: psycopg.Connection = Depends(get_conn)):
    booking = svc.cancel_booking(conn, user, booking_id, data.note if data else None)
    promote_quietly(conn, booking["room_id"])
    return booking


@router.post("/bookings/{booking_id}/approve", response_model=BookingOut)
def approve(booking_id: int, user: dict = Depends(staff), conn: psycopg.Connection = Depends(get_conn)):
    return svc.approve_booking(conn, user, booking_id)


@router.post("/bookings/{booking_id}/reject", response_model=BookingOut)
def reject(booking_id: int, data: RejectIn, user: dict = Depends(staff),
           conn: psycopg.Connection = Depends(get_conn)):
    booking = svc.reject_booking(conn, user, booking_id, data.reason)
    promote_quietly(conn, booking["room_id"])
    return booking


@router.post("/checkin/scan", response_model=BookingOut)
def scan_ticket(data: TicketScanIn, user: dict = Depends(staff), conn: psycopg.Connection = Depends(get_conn)):
    """An approver or admin scans the QR e-ticket the booker shows at the room (or types the code under it)."""
    return checkin_service.scan_ticket(conn, user, data.code)


@router.post("/bookings/{booking_id}/staff-check-in", response_model=BookingOut)
def staff_check_in(booking_id: int, data: StaffCheckInIn, user: dict = Depends(staff),
                   conn: psycopg.Connection = Depends(get_conn)):
    """Staff confirm the booker came, e.g. no phone at the door or a no-show recorded by mistake."""
    return checkin_service.staff_check_in(conn, user, booking_id, data.note)
