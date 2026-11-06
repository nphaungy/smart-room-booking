"""RB05-RB09: edit, cancel, approve/reject, status history, notifications, permissions (BR04, BR06, BR09, BR10)."""
from .conftest import slot


def code(r):
    return r.json()["error"]["code"]


def notif_types(client, headers):
    return [n["type"] for n in client.get("/api/notifications", headers=headers).json()]


def test_approve_flow_with_notifications_and_log(client, h, book):
    b = book().json()
    assert b["status"] == "PENDING" and b["can_edit"] and b["can_cancel"] and not b["can_decide"]
    assert "BOOKING_REQUESTED" in notif_types(client, h["approver"])

    queue = client.get("/api/bookings?status=PENDING", headers=h["approver"]).json()
    assert [x["id"] for x in queue] == [b["id"]] and queue[0]["can_decide"]

    r = client.post(f"/api/bookings/{b['id']}/approve", headers=h["approver"])
    assert r.status_code == 200 and r.json()["status"] == "APPROVED"
    assert r.json()["approver_name"] == "เจ้าหน้าที่อาคาร"
    assert "BOOKING_APPROVED" in notif_types(client, h["s1"])

    detail = client.get(f"/api/bookings/{b['id']}", headers=h["s1"]).json()
    assert [(l["from_status"], l["to_status"]) for l in detail["logs"]] == [(None, "PENDING"), ("PENDING", "APPROVED")]
    assert not detail["can_edit"]  # BR10: only PENDING can be edited

    # cannot decide twice
    r = client.post(f"/api/bookings/{b['id']}/reject", headers=h["approver"], json={"reason": "x"})
    assert r.status_code == 409 and code(r) == "INVALID_STATUS"


def test_reject_requires_reason_and_frees_slot(client, h, book):
    b = book().json()
    r = client.post(f"/api/bookings/{b['id']}/reject", headers=h["approver"], json={"reason": ""})
    assert r.status_code == 422
    r = client.post(f"/api/bookings/{b['id']}/reject", headers=h["approver"], json={"reason": "   "})
    assert r.status_code == 422 and code(r) == "REASON_REQUIRED"
    r = client.post(f"/api/bookings/{b['id']}/reject", headers=h["approver"], json={"reason": "มีกิจกรรมคณะ"})
    assert r.status_code == 200 and r.json()["reject_reason"] == "มีกิจกรรมคณะ"
    msgs = client.get("/api/notifications", headers=h["s1"]).json()
    assert msgs[0]["type"] == "BOOKING_REJECTED" and "มีกิจกรรมคณะ" in msgs[0]["message"]
    assert book(who="s2").status_code == 201  # rejected booking no longer holds the slot


def test_booker_cannot_approve(client, h, book):
    b = book().json()
    assert client.post(f"/api/bookings/{b['id']}/approve", headers=h["s1"]).status_code == 403
    assert client.post(f"/api/bookings/{b['id']}/approve", headers=h["s2"]).status_code == 403


def test_cancel_permissions_and_slot_release(client, h, book):
    b = book().json()
    # BR04: another booker cannot cancel or even view it
    assert client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s2"]).status_code == 403
    assert client.get(f"/api/bookings/{b['id']}", headers=h["s2"]).status_code == 403
    r = client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s1"], json={"note": "เปลี่ยนแผน"})
    assert r.status_code == 200 and r.json()["status"] == "CANCELLED"
    # cancelled is final
    assert client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s1"]).status_code == 409
    assert book(who="s2").status_code == 201


def test_owner_cancel_of_approved_notifies_approvers(client, h, book):
    b = book().json()
    client.post(f"/api/bookings/{b['id']}/approve", headers=h["approver"])
    client.post(f"/api/bookings/{b['id']}/cancel", headers=h["s1"])
    assert "BOOKING_CANCELLED" in notif_types(client, h["approver"])


def test_admin_cancel_notifies_booker(client, h, book):
    b = book().json()
    r = client.post(f"/api/bookings/{b['id']}/cancel", headers=h["admin"], json={"note": "ใช้จัดสอบ"})
    assert r.status_code == 200
    assert "BOOKING_CANCELLED" in notif_types(client, h["s1"])


def test_br06_cancel_too_late(client, h, db):
    # inserted directly: a booking starting in 30 minutes (the API itself only allows aligned future slots)
    bid = db.execute("INSERT INTO bookings (room_id, booker_id, start_time, end_time, attendees, purpose) "
                     "VALUES (3, 3, now() + interval '30 min', now() + interval '90 min', 5, 'x') "
                     "RETURNING id").fetchone()[0]
    r = client.post(f"/api/bookings/{bid}/cancel", headers=h["s1"])
    assert r.status_code == 409 and code(r) == "CANCEL_TOO_LATE"
    assert client.post(f"/api/bookings/{bid}/cancel", headers=h["admin"]).status_code == 200  # admin is exempt


def test_edit_pending_booking(client, h, book):
    b = book().json()
    book(who="s2", start=slot(1, 13), end=slot(1, 14))
    payload = {"room_id": 1, "start_time": slot(1, 10), "end_time": slot(1, 11),
               "attendees": 15, "purpose": "เลื่อนเวลา", "version": b["version"]}
    r = client.put(f"/api/bookings/{b['id']}", headers=h["s1"], json=payload)
    assert r.status_code == 200 and r.json()["version"] == 2 and r.json()["attendees"] == 15
    # overlapping its own old range is fine; moving onto someone else's is not
    r = client.put(f"/api/bookings/{b['id']}", headers=h["s1"],
                   json={**payload, "start_time": slot(1, 13, 30), "end_time": slot(1, 14, 30), "version": None})
    assert r.status_code == 409 and code(r) == "BOOKING_CONFLICT"
    # stale version
    r = client.put(f"/api/bookings/{b['id']}", headers=h["s1"], json={**payload, "version": 1})
    assert r.status_code == 409 and code(r) == "STALE_VERSION"
    # someone else's booking
    assert client.put(f"/api/bookings/{b['id']}", headers=h["s2"], json=payload).status_code == 403


def test_my_bookings_history(client, h, book):
    book(start=slot(1, 9), end=slot(1, 10))
    b2 = book(start=slot(2, 9), end=slot(2, 10)).json()
    client.post(f"/api/bookings/{b2['id']}/cancel", headers=h["s1"])
    all_mine = client.get("/api/bookings/me", headers=h["s1"]).json()
    assert len(all_mine) == 2
    cancelled = client.get("/api/bookings/me?status=CANCELLED", headers=h["s1"]).json()
    assert [x["id"] for x in cancelled] == [b2["id"]]
    assert client.get("/api/bookings/me", headers=h["s2"]).json() == []


def test_notifications_polling_and_read(client, h, book):
    book()
    items = client.get("/api/notifications", headers=h["approver"]).json()
    last = items[0]["id"]
    assert client.get(f"/api/notifications?after_id={last}", headers=h["approver"]).json() == []
    client.post(f"/api/notifications/{last}/read", headers=h["approver"])
    assert client.get("/api/notifications?unread_only=true", headers=h["approver"]).json() == []
