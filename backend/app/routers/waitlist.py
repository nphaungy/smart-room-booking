"""Waitlist: queue for a room at a time that is already taken."""
import psycopg
from fastapi import APIRouter, Depends

from ..db import get_conn
from ..models import BookingIn, WaitlistEntryOut
from ..security import get_current_user
from ..services import waitlist_service as svc

router = APIRouter(tags=["Waitlist"])


@router.post("/waitlist", response_model=WaitlistEntryOut, status_code=201)
def join(data: BookingIn, user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return svc.join(conn, user, data)


@router.get("/waitlist/me", response_model=list[WaitlistEntryOut])
def mine(user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return svc.list_mine(conn, user)


@router.post("/waitlist/{entry_id}/cancel", response_model=WaitlistEntryOut)
def cancel(entry_id: int, user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    return svc.cancel(conn, user, entry_id)
