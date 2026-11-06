"""RB01: register and log in."""
import psycopg
from fastapi import APIRouter, Depends

from ..config import get_settings
from ..db import get_conn
from ..errors import ApiError
from ..models import LoginIn, RegisterIn, TokenOut, UserOut
from ..security import create_token, get_current_user, hash_password, verify_password

router = APIRouter(prefix="/auth", tags=["Auth (RB01)"])


@router.post("/register", response_model=TokenOut, status_code=201)
def register(data: RegisterIn, conn: psycopg.Connection = Depends(get_conn)):
    email = data.email.lower()
    domain = get_settings().allowed_email_domain.strip().lower()
    if domain and not email.endswith("@" + domain):
        raise ApiError(422, "EMAIL_DOMAIN_NOT_ALLOWED", f"ต้องใช้อีเมล @{domain} เท่านั้น")
    if conn.execute("SELECT 1 FROM users WHERE email = %s", (email,)).fetchone():
        raise ApiError(409, "EMAIL_TAKEN", "อีเมลนี้ถูกใช้สมัครแล้ว")
    user = conn.execute(
        "INSERT INTO users (email, password_hash, full_name, faculty) VALUES (%s, %s, %s, %s) "
        "RETURNING id, email, full_name, role, faculty",
        (email, hash_password(data.password), data.full_name.strip(), data.faculty),
    ).fetchone()  # new accounts always start as BOOKER; only an admin can promote
    return {"access_token": create_token(user["id"], user["role"]), "user": user}


@router.post("/login", response_model=TokenOut)
def login(data: LoginIn, conn: psycopg.Connection = Depends(get_conn)):
    row = conn.execute(
        "SELECT id, email, full_name, role, faculty, password_hash FROM users WHERE email = %s",
        (data.email.lower(),),
    ).fetchone()
    if row is None or not verify_password(data.password, row["password_hash"]):
        raise ApiError(401, "INVALID_CREDENTIALS", "อีเมลหรือรหัสผ่านไม่ถูกต้อง")
    row.pop("password_hash")
    return {"access_token": create_token(row["id"], row["role"]), "user": row}


@router.get("/me", response_model=UserOut)
def me(user: dict = Depends(get_current_user)):
    return user
