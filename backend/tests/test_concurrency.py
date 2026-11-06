"""BR05: many users submit the same slot at the same instant -> exactly one wins.

Starts a real uvicorn server (multiple worker threads, separate DB connections)
and fires requests in parallel, so this is a true race, not a sequential test.
"""
import os
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

import httpx
import pytest

from .conftest import TEST_DB, slot

N = 20


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture(scope="module")
def server():
    port = _free_port()
    env = {**os.environ, "DATABASE_URL": TEST_DB}
    proc = subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "app.main:app", "--port", str(port), "--log-level", "warning"],
        cwd=Path(__file__).resolve().parents[1], env=env,
    )
    base = f"http://127.0.0.1:{port}"
    for _ in range(100):
        try:
            if httpx.get(base + "/health").status_code == 200:
                break
        except httpx.HTTPError:
            time.sleep(0.1)
    else:
        proc.kill()
        pytest.fail("server did not start")
    yield base
    proc.terminate()
    proc.wait(10)


def _register_users(base: str, n: int) -> list[dict]:
    headers = []
    for i in range(n):
        email = f"race{i}_{time.time_ns()}@example.com"
        r = httpx.post(base + "/api/auth/register", json={"email": email, "password": "Passw0rd!", "full_name": f"R{i}"})
        assert r.status_code == 201, r.text
        headers.append({"Authorization": f"Bearer {r.json()['access_token']}"})
    return headers


def _fire_together(base: str, jobs: list[tuple[dict, dict]]) -> list[int]:
    """Release all requests at the same moment with a barrier."""
    barrier = threading.Barrier(len(jobs))
    results: list[int] = [0] * len(jobs)

    def run(i, headers, payload):
        with httpx.Client(base_url=base, timeout=30) as c:
            barrier.wait()
            results[i] = c.post("/api/bookings", headers=headers, json=payload).status_code

    threads = [threading.Thread(target=run, args=(i, h, p)) for i, (h, p) in enumerate(jobs)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return results


def test_same_slot_only_one_wins(server, db):
    users = _register_users(server, N)
    payload = {"room_id": 1, "start_time": slot(3, 13), "end_time": slot(3, 15), "attendees": 5, "purpose": "race"}
    results = _fire_together(server, [(u, payload) for u in users])
    assert results.count(201) == 1, results
    assert results.count(409) == N - 1, results
    active = db.execute("SELECT count(*) FROM bookings WHERE room_id = 1 AND status IN ('PENDING','APPROVED')").fetchone()[0]
    assert active == 1


def test_partially_overlapping_ranges(server, db):
    """Different but overlapping ranges submitted together: the winners must never overlap."""
    users = _register_users(server, 8)
    ranges = [(13, 0, 14, 0), (13, 30, 14, 30), (14, 0, 15, 0), (14, 30, 15, 30),
              (15, 0, 16, 0), (13, 0, 16, 0), (15, 30, 16, 30), (16, 0, 17, 0)]
    jobs = [(u, {"room_id": 2, "start_time": slot(4, a, b), "end_time": slot(4, c, d), "attendees": 5, "purpose": "race"})
            for u, (a, b, c, d) in zip(users, ranges)]
    results = _fire_together(server, jobs)
    assert set(results) <= {201, 409}, results
    overlaps = db.execute(
        "SELECT count(*) FROM bookings x JOIN bookings y ON x.id < y.id AND x.room_id = y.room_id "
        "WHERE x.room_id = 2 AND x.status IN ('PENDING','APPROVED') AND y.status IN ('PENDING','APPROVED') "
        "AND tstzrange(x.start_time, x.end_time, '[)') && tstzrange(y.start_time, y.end_time, '[)')"
    ).fetchone()[0]
    assert overlaps == 0
    assert results.count(201) >= 2  # e.g. 13:00-14:00 + 14:00-15:00 can both succeed


def test_two_approvers_decide_at_once(server, db):
    """Row lock (SELECT ... FOR UPDATE): only one decision is accepted."""
    login = lambda e, p: {"Authorization": "Bearer " + httpx.post(
        server + "/api/auth/login", json={"email": e, "password": p}).json()["access_token"]}
    s1, approver, admin = (login("student1@example.com", "Student1234"),
                           login("approver@example.com", "Approver1234"),
                           login("admin@example.com", "Admin1234"))
    b = httpx.post(server + "/api/bookings", headers=s1, json={
        "room_id": 4, "start_time": slot(5, 9), "end_time": slot(5, 10), "attendees": 5, "purpose": "x"}).json()
    barrier = threading.Barrier(2)
    out = {}

    def act(name, headers, path, body):
        barrier.wait()
        out[name] = httpx.post(server + f"/api/bookings/{b['id']}/{path}", headers=headers, json=body).status_code

    t1 = threading.Thread(target=act, args=("approve", approver, "approve", None))
    t2 = threading.Thread(target=act, args=("reject", admin, "reject", {"reason": "ชน"}))
    t1.start(); t2.start(); t1.join(); t2.join()
    assert sorted(out.values()) == [200, 409], out
    logs = db.execute("SELECT count(*) FROM booking_status_logs WHERE booking_id = %s", (b["id"],)).fetchone()[0]
    assert logs == 2  # created + exactly one decision
