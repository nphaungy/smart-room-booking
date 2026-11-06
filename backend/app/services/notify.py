import psycopg


def notify(conn: psycopg.Connection, user_ids: list[int], booking_id: int | None,
           type_: str, title: str, message: str) -> None:
    for uid in set(user_ids):
        conn.execute(
            "INSERT INTO notifications (user_id, booking_id, type, title, message) VALUES (%s, %s, %s, %s, %s)",
            (uid, booking_id, type_, title, message),
        )


def approver_ids(conn: psycopg.Connection) -> list[int]:
    rows = conn.execute("SELECT id FROM users WHERE role IN ('APPROVER', 'ADMIN')").fetchall()
    return [r["id"] for r in rows]
