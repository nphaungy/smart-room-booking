import os
from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

# Must be set before the app is imported (settings are cached).
os.environ.setdefault(
    "DATABASE_URL", "postgresql://postgres@/roombooking_test?host=/var/tmp/pgrb&port=5433"
)
TEST_DB = os.environ["DATABASE_URL"]

import psycopg  # noqa: E402
import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402
from app.seed import seed  # noqa: E402

TZ = ZoneInfo("Asia/Bangkok")


def slot(day_offset: int = 1, hour: int = 9, minute: int = 0) -> str:
    d = datetime.now(TZ).date() + timedelta(days=day_offset)
    return datetime.combine(d, time(hour, minute), tzinfo=TZ).isoformat()


@pytest.fixture(scope="session", autouse=True)
def database():
    with psycopg.connect(TEST_DB, autocommit=True) as conn:
        conn.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
    seed(TEST_DB)
    yield


@pytest.fixture(autouse=True)
def clean_bookings(database):
    with psycopg.connect(TEST_DB, autocommit=True) as conn:
        conn.execute("TRUNCATE bookings, booking_status_logs, notifications, room_closures RESTART IDENTITY CASCADE")
        conn.execute("UPDATE rooms SET status = 'ACTIVE'")
    yield


@pytest.fixture(scope="session")
def client(database):
    with TestClient(app) as c:
        yield c


def _login(client, email, pw):
    r = client.post("/api/auth/login", json={"email": email, "password": pw})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest.fixture(scope="session")
def h(client):
    """Auth headers for each demo role."""
    return {
        "admin": _login(client, "admin@example.com", "Admin1234"),
        "approver": _login(client, "approver@example.com", "Approver1234"),
        "s1": _login(client, "student1@example.com", "Student1234"),
        "s2": _login(client, "student2@example.com", "Student1234"),
    }


@pytest.fixture
def book(client, h):
    def _book(who="s1", room_id=1, start=None, end=None, attendees=10, purpose="ประชุม"):
        return client.post("/api/bookings", headers=h[who], json={
            "room_id": room_id, "start_time": start or slot(1, 9), "end_time": end or slot(1, 10),
            "attendees": attendees, "purpose": purpose,
        })
    return _book


@pytest.fixture
def db():
    with psycopg.connect(TEST_DB, autocommit=True) as conn:
        yield conn
