from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from psycopg import errors as pg


class ApiError(Exception):
    """Business error returned to the app as {"error": {"code", "message"}}."""

    def __init__(self, status: int, code: str, message: str, details: dict | None = None):
        self.status = status
        self.code = code
        self.message = message
        self.details = details or {}


def _body(code: str, message: str, details: dict | None = None) -> dict:
    return {"error": {"code": code, "message": message, "details": details or {}}}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def api_error(_: Request, exc: ApiError):
        return JSONResponse(status_code=exc.status, content=_body(exc.code, exc.message, exc.details))

    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, exc: RequestValidationError):
        fields = {".".join(str(p) for p in e["loc"] if p != "body"): e["msg"] for e in exc.errors()}
        return JSONResponse(status_code=422, content=_body("VALIDATION_ERROR", "ข้อมูลไม่ถูกต้อง", fields))

    # [BR02/BR05] the database rejected an overlapping booking (possibly a simultaneous one)
    @app.exception_handler(pg.ExclusionViolation)
    async def exclusion(_: Request, exc: pg.ExclusionViolation):
        return JSONResponse(
            status_code=409,
            content=_body("BOOKING_CONFLICT", "ช่วงเวลานี้ถูกจองแล้ว กรุณาเลือกเวลาอื่น"),
        )

    # Last-resort mapping if a deadlock survives the service-level retries
    @app.exception_handler(pg.DeadlockDetected)
    async def deadlock(_: Request, exc: pg.DeadlockDetected):
        return JSONResponse(status_code=409, content=_body("BUSY_RETRY", "มีผู้ใช้จองพร้อมกัน กรุณาลองใหม่อีกครั้ง"))

    @app.exception_handler(pg.CheckViolation)
    async def check(_: Request, exc: pg.CheckViolation):
        constraint = getattr(exc.diag, "constraint_name", None) or "check"
        return JSONResponse(status_code=422, content=_body("RULE_VIOLATION", f"ข้อมูลขัดกับกฎ ({constraint})"))

    @app.exception_handler(pg.UniqueViolation)
    async def unique(_: Request, exc: pg.UniqueViolation):
        constraint = getattr(exc.diag, "constraint_name", None) or "unique"
        return JSONResponse(status_code=409, content=_body("DUPLICATE", f"ข้อมูลซ้ำ ({constraint})"))

    @app.exception_handler(pg.ForeignKeyViolation)
    async def fk(_: Request, exc: pg.ForeignKeyViolation):
        return JSONResponse(status_code=422, content=_body("INVALID_REFERENCE", "อ้างอิงข้อมูลที่ไม่มีอยู่"))
