from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
import psycopg
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import get_settings
from .db import get_conn
from .errors import ApiError

_bearer = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    return bcrypt.checkpw(password.encode(), hashed.encode())


def create_token(user_id: int, role: str) -> str:
    s = get_settings()
    payload = {
        "sub": str(user_id),
        "role": role,
        "exp": datetime.now(timezone.utc) + timedelta(hours=s.jwt_expire_hours),
    }
    return jwt.encode(payload, s.jwt_secret, algorithm="HS256")


def get_current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
    conn: psycopg.Connection = Depends(get_conn),
) -> dict:
    if creds is None:
        raise ApiError(401, "UNAUTHENTICATED", "กรุณาเข้าสู่ระบบ")
    try:
        payload = jwt.decode(creds.credentials, get_settings().jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError:
        raise ApiError(401, "INVALID_TOKEN", "Session หมดอายุ กรุณาเข้าสู่ระบบใหม่")
    # Role is re-read from the DB on every request, so a role change takes effect immediately.
    user = conn.execute(
        "SELECT id, email, full_name, role, faculty, created_at FROM users WHERE id = %s",
        (int(payload["sub"]),),
    ).fetchone()
    if user is None:
        raise ApiError(401, "INVALID_TOKEN", "ไม่พบผู้ใช้")
    return user


def require_roles(*roles: str):
    """[BR04] permission checks happen on the server, never only in the app."""

    def dep(user: dict = Depends(get_current_user)) -> dict:
        if user["role"] not in roles:
            raise ApiError(403, "FORBIDDEN", "คุณไม่มีสิทธิ์ทำรายการนี้")
        return user

    return dep
