"""Monthly reports for approvers and admins."""
import psycopg
from fastapi import APIRouter, Depends, Query, Response

from ..db import get_conn
from ..models import MonthlyReportOut
from ..security import require_roles
from ..services.report_service import monthly_report, report_csv

router = APIRouter(prefix="/reports", tags=["Reports"])
staff = require_roles("APPROVER", "ADMIN")


@router.get("/monthly", response_model=MonthlyReportOut)
def monthly(year: int = Query(ge=2000, le=2100), month: int = Query(ge=1, le=12), user: dict = Depends(staff),
            conn: psycopg.Connection = Depends(get_conn)):
    return monthly_report(conn, year, month)


@router.get("/monthly.csv")
def monthly_csv(year: int = Query(ge=2000, le=2100), month: int = Query(ge=1, le=12), user: dict = Depends(staff),
                conn: psycopg.Connection = Depends(get_conn)):
    return Response(
        report_csv(monthly_report(conn, year, month)),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="room-report-{year}-{month:02d}.csv"'},
    )
