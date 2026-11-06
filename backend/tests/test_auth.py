"""RB01 register / login, and BR04 (permissions are checked on the server)."""


def test_register_login_me(client):
    r = client.post("/api/auth/register", json={
        "email": "New.User@Example.com", "password": "Passw0rd!", "full_name": "ผู้ใช้ใหม่"})
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["user"]["email"] == "new.user@example.com"
    assert body["user"]["role"] == "BOOKER"  # nobody can self-register as approver/admin

    me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {body['access_token']}"})
    assert me.status_code == 200 and me.json()["full_name"] == "ผู้ใช้ใหม่"


def test_duplicate_email(client):
    r = client.post("/api/auth/register", json={
        "email": "student1@example.com", "password": "Passw0rd!", "full_name": "x"})
    assert r.status_code == 409 and r.json()["error"]["code"] == "EMAIL_TAKEN"


def test_short_password_rejected(client):
    r = client.post("/api/auth/register", json={"email": "a@example.com", "password": "123", "full_name": "x"})
    assert r.status_code == 422 and r.json()["error"]["code"] == "VALIDATION_ERROR"


def test_wrong_password(client):
    r = client.post("/api/auth/login", json={"email": "student1@example.com", "password": "nope"})
    assert r.status_code == 401 and r.json()["error"]["code"] == "INVALID_CREDENTIALS"


def test_requires_token(client):
    assert client.get("/api/rooms").status_code == 401
    bad = client.get("/api/rooms", headers={"Authorization": "Bearer garbage"})
    assert bad.status_code == 401 and bad.json()["error"]["code"] == "INVALID_TOKEN"


def test_role_guards(client, h):
    assert client.get("/api/bookings", headers=h["s1"]).status_code == 403        # booker can't see all bookings
    assert client.get("/api/dashboard", headers=h["s1"]).status_code == 403
    assert client.get("/api/admin/users", headers=h["approver"]).status_code == 403  # approver isn't admin
    assert client.get("/api/admin/users", headers=h["admin"]).status_code == 200
