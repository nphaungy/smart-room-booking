"""RB08 notifications and RB09 dashboard."""
from datetime import date, timedelta

import psycopg
from fastapi import APIRouter, Depends, Response

from ..config import get_settings
from ..db import get_conn
from ..models import DashboardOut, NotificationOut
from ..rules import now_utc
from ..security import get_current_user, require_roles
from ..services.dashboard_service import dashboard

router = APIRouter(tags=["Notifications (RB08) & Dashboard (RB09)"])


@router.get("/notifications", response_model=list[NotificationOut])
def notifications(after_id: int = 0, unread_only: bool = False, user: dict = Depends(get_current_user),
                  conn: psycopg.Connection = Depends(get_conn)):
    """The app's WorkManager polls with ?after_id=<last id seen> and shows a system notification for each new row."""
    return conn.execute(
        "SELECT id, booking_id, type, title, message, is_read, created_at FROM notifications "
        "WHERE user_id = %s AND id > %s AND (NOT %s OR NOT is_read) ORDER BY id DESC LIMIT 100",
        (user["id"], after_id, unread_only),
    ).fetchall()


@router.post("/notifications/{notification_id}/read", status_code=204)
def mark_read(notification_id: int, user: dict = Depends(get_current_user),
              conn: psycopg.Connection = Depends(get_conn)):
    conn.execute("UPDATE notifications SET is_read = true WHERE id = %s AND user_id = %s",
                 (notification_id, user["id"]))
    return Response(status_code=204)


@router.post("/notifications/read-all", status_code=204)
def mark_all_read(user: dict = Depends(get_current_user), conn: psycopg.Connection = Depends(get_conn)):
    conn.execute("UPDATE notifications SET is_read = true WHERE user_id = %s", (user["id"],))
    return Response(status_code=204)


@router.get("/dashboard", response_model=DashboardOut)
def get_dashboard(date_from: date | None = None, date_to: date | None = None,
                  user: dict = Depends(require_roles("APPROVER", "ADMIN")),
                  conn: psycopg.Connection = Depends(get_conn)):
    """Defaults to the current calendar month."""
    today = now_utc().astimezone(get_settings().tz).date()
    date_from = date_from or today.replace(day=1)
    if date_to is None:
        next_month = (date_from.replace(day=28) + timedelta(days=4)).replace(day=1)
        date_to = next_month - timedelta(days=1)
    return dashboard(conn, date_from, date_to)
