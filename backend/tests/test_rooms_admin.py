"""RB02-RB04 search & schedule, RB09 dashboard, RB10 admin."""
from .conftest import slot


def test_list_and_filter_rooms(client, h):
    rooms = client.get("/api/rooms", headers=h["s1"]).json()
    assert len(rooms) == 7 and all("equipment" in r for r in rooms)
    big = client.get("/api/rooms?attendees=41", headers=h["s1"]).json()
    assert {r["code"] for r in big} == {"SC-401", "CS-LAB1"}
    labs = client.get("/api/rooms?q=LAB", headers=h["s1"]).json()
    assert {r["code"] for r in labs} == {"CS-LAB1", "CS-LAB2"}
    eq = {e["name"]: e["id"] for e in client.get("/api/equipment", headers=h["s1"]).json()}
    both = client.get(f"/api/rooms?equipment_ids={eq['ไมโครโฟน']}&equipment_ids={eq['โปรเจกเตอร์']}",
                      headers=h["s1"]).json()
    assert {r["code"] for r in both} == {"SC-301", "SC-401"}


def test_search_by_time_excludes_busy_rooms(client, h, book):
    book(room_id=1, start=slot(1, 9), end=slot(1, 11))
    params = {"start": slot(1, 10), "end": slot(1, 12), "attendees": 30}
    free = client.get("/api/rooms", headers=h["s2"], params=params).json()
    codes = {r["code"] for r in free}
    assert "SC-301" not in codes and {"SC-401", "CS-LAB1", "CS-LAB2"} <= codes
    r = client.get("/api/rooms", headers=h["s2"], params={"start": slot(1, 12), "end": slot(1, 10)})
    assert r.status_code == 422


def test_schedule_free_busy_and_privacy(client, h, book):
    b = book(start=slot(1, 9), end=slot(1, 10, 30), purpose="ลับ").json()
    other = client.get(f"/api/rooms/1/schedule?date={slot(1)[:10]}", headers=h["s2"]).json()
    assert other["busy"][0]["label"] == "ถูกจองแล้ว" and other["busy"][0]["booking_id"] is None
    assert [(f["start_time"][11:16], f["end_time"][11:16]) for f in other["free"]] == [("08:00", "09:00"),
                                                                                    ("10:30", "20:00")]
    mine = client.get(f"/api/rooms/1/schedule?date={slot(1)[:10]}", headers=h["s1"]).json()
    assert mine["busy"][0]["is_mine"] and mine["busy"][0]["booking_id"] == b["id"]
    staff = client.get(f"/api/rooms/1/schedule?date={slot(1)[:10]}", headers=h["approver"]).json()
    assert "ลับ" in staff["busy"][0]["label"]


def test_admin_room_crud(client, h):
    types = client.get("/api/room-types", headers=h["admin"]).json()
    payload = {"code": "NEW-101", "name": "ห้องใหม่", "building": "อาคารใหม่", "floor": 1, "capacity": 20,
               "type_id": types[0]["id"], "equipment": [{"equipment_id": 1, "quantity": 2}]}
    assert client.post("/api/admin/rooms", headers=h["s1"], json=payload).status_code == 403
    r = client.post("/api/admin/rooms", headers=h["admin"], json=payload)
    assert r.status_code == 201 and r.json()["equipment"][0]["quantity"] == 2
    room_id = r.json()["id"]
    assert client.post("/api/admin/rooms", headers=h["admin"], json=payload).status_code == 409  # duplicate code
    r = client.put(f"/api/admin/rooms/{room_id}", headers=h["admin"],
                   json={**payload, "status": "INACTIVE", "equipment": []})
    assert r.status_code == 200 and r.json()["status"] == "INACTIVE" and r.json()["equipment"] == []
    assert room_id not in [x["id"] for x in client.get("/api/rooms", headers=h["s1"]).json()]
    assert room_id in [x["id"] for x in client.get("/api/admin/rooms", headers=h["admin"]).json()]


def test_closure_conflicts(client, h, book):
    b = book(start=slot(1, 9), end=slot(1, 10)).json()
    body = {"start_time": slot(1, 8), "end_time": slot(1, 12), "reason": "ทาสีห้อง"}
    r = client.post("/api/admin/rooms/1/closures", headers=h["admin"], json=body)
    assert r.status_code == 409 and r.json()["error"]["details"]["booking_ids"] == [b["id"]]
    r = client.post("/api/admin/rooms/1/closures?cancel_conflicts=true", headers=h["admin"], json=body)
    assert r.status_code == 201
    assert client.get(f"/api/bookings/{b['id']}", headers=h["s1"]).json()["status"] == "CANCELLED"
    assert "ห้องปิดปรับปรุง" in client.get("/api/notifications", headers=h["s1"]).json()[0]["title"]
    closures = client.get("/api/admin/rooms/1/closures", headers=h["admin"]).json()
    assert client.delete(f"/api/admin/closures/{closures[0]['id']}", headers=h["admin"]).status_code == 204


def test_admin_roles(client, h):
    users = client.get("/api/admin/users", headers=h["admin"]).json()
    admin_id = next(u["id"] for u in users if u["role"] == "ADMIN")
    r = client.patch(f"/api/admin/users/{admin_id}/role", headers=h["admin"], json={"role": "BOOKER"})
    assert r.status_code == 409


def test_dashboard(client, h, book):
    b1 = book(room_id=1, start=slot(1, 9), end=slot(1, 11)).json()
    b2 = book(room_id=2, start=slot(1, 9), end=slot(1, 10)).json()
    book(room_id=3, start=slot(1, 13), end=slot(1, 14))
    client.post(f"/api/bookings/{b1['id']}/approve", headers=h["approver"])
    client.post(f"/api/bookings/{b2['id']}/reject", headers=h["approver"], json={"reason": "ไม่ว่าง"})
    d = slot(1)[:10]
    r = client.get(f"/api/dashboard?date_from={d}&date_to={d}", headers=h["approver"]).json()
    assert r["total_bookings"] == 3 and r["pending_now"] == 1
    assert {s["status"]: s["count"] for s in r["by_status"]} == {"PENDING": 1, "APPROVED": 1, "REJECTED": 1}
    assert r["approval_rate_percent"] == 50.0
    top = r["rooms"][0]
    assert top["room_code"] == "SC-301" and top["approved_hours"] == 2.0
    assert top["utilization_percent"] == round(100 * 2 / 12, 2)  # 2 of 12 opening hours
    assert {x["hour"]: x["count"] for x in r["by_hour"]} == {9: 1, 13: 1}
    # default range (this month) also works
    assert client.get("/api/dashboard", headers=h["admin"]).status_code == 200
