"""Demo data. Run:  python -m app.seed   (safe to run more than once)."""
import psycopg

from .config import get_settings
from .db import apply_schema
from .security import hash_password

USERS = [
    ("admin@example.com", "Admin1234", "ผู้ดูแลระบบ", "ADMIN", "สำนักบริการคอมพิวเตอร์"),
    ("approver@example.com", "Approver1234", "เจ้าหน้าที่อาคาร", "APPROVER", "งานอาคารสถานที่"),
    ("student1@example.com", "Student1234", "สมชาย ใจดี", "BOOKER", "วิทยาการคอมพิวเตอร์"),
    ("student2@example.com", "Student1234", "สมหญิง รักเรียน", "BOOKER", "วิทยาการคอมพิวเตอร์"),
]
ROOM_TYPES = ["ห้องประชุม", "ห้องเรียน", "ห้องปฏิบัติการคอมพิวเตอร์", "ห้องติวกลุ่มย่อย"]
EQUIPMENT = ["โปรเจกเตอร์", "ไมโครโฟน", "ไวท์บอร์ด", "คอมพิวเตอร์", "จอทีวี", "ระบบประชุมออนไลน์"]
# code, name, building, floor, capacity, type, [(equipment, qty)]
ROOMS = [
    ("SC-301", "ห้องประชุมใหญ่", "อาคารวิทยาศาสตร์", 3, 40, "ห้องประชุม",
     [("โปรเจกเตอร์", 1), ("ไมโครโฟน", 4), ("ระบบประชุมออนไลน์", 1)]),
    ("SC-302", "ห้องประชุมเล็ก", "อาคารวิทยาศาสตร์", 3, 12, "ห้องประชุม", [("จอทีวี", 1), ("ไวท์บอร์ด", 1)]),
    ("SC-401", "ห้องเรียนรวม", "อาคารวิทยาศาสตร์", 4, 80, "ห้องเรียน",
     [("โปรเจกเตอร์", 2), ("ไมโครโฟน", 2), ("ไวท์บอร์ด", 2)]),
    ("CS-LAB1", "ห้องแล็บคอมพิวเตอร์ 1", "อาคารคอมพิวเตอร์", 2, 45, "ห้องปฏิบัติการคอมพิวเตอร์",
     [("คอมพิวเตอร์", 45), ("โปรเจกเตอร์", 1)]),
    ("CS-LAB2", "ห้องแล็บคอมพิวเตอร์ 2", "อาคารคอมพิวเตอร์", 2, 30, "ห้องปฏิบัติการคอมพิวเตอร์",
     [("คอมพิวเตอร์", 30), ("จอทีวี", 1)]),
    ("LIB-G1", "ห้องติวกลุ่ม 1", "หอสมุด", 1, 6, "ห้องติวกลุ่มย่อย", [("ไวท์บอร์ด", 1), ("จอทีวี", 1)]),
    ("LIB-G2", "ห้องติวกลุ่ม 2", "หอสมุด", 1, 8, "ห้องติวกลุ่มย่อย", [("ไวท์บอร์ด", 1)]),
]


def seed(conninfo: str | None = None) -> None:
    conninfo = conninfo or get_settings().database_url
    apply_schema(conninfo)
    with psycopg.connect(conninfo) as conn:
        for email, pw, name, role, faculty in USERS:
            conn.execute(
                "INSERT INTO users (email, password_hash, full_name, role, faculty) VALUES (%s, %s, %s, %s, %s) "
                "ON CONFLICT (email) DO NOTHING",
                (email, hash_password(pw), name, role, faculty),
            )
        for t in ROOM_TYPES:
            conn.execute("INSERT INTO room_types (name) VALUES (%s) ON CONFLICT (name) DO NOTHING", (t,))
        for e in EQUIPMENT:
            conn.execute("INSERT INTO equipment (name) VALUES (%s) ON CONFLICT (name) DO NOTHING", (e,))
        for code, name, building, floor, cap, type_name, eq in ROOMS:
            room_id = conn.execute(
                "INSERT INTO rooms (code, name, building, floor, capacity, type_id) "
                "SELECT %s, %s, %s, %s, %s, id FROM room_types WHERE name = %s "
                "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                (code, name, building, floor, cap, type_name),
            ).fetchone()[0]
            for eq_name, qty in eq:
                conn.execute(
                    "INSERT INTO room_equipment (room_id, equipment_id, quantity) "
                    "SELECT %s, id, %s FROM equipment WHERE name = %s ON CONFLICT DO NOTHING",
                    (room_id, qty, eq_name),
                )
    print("Seed complete. Demo accounts:")
    for email, pw, _, role, _ in USERS:
        print(f"  {role:<9} {email} / {pw}")


if __name__ == "__main__":
    seed()
