from collections.abc import Iterator
from pathlib import Path

import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from .config import get_settings

_pool: ConnectionPool | None = None


def init_pool(conninfo: str | None = None) -> ConnectionPool:
    global _pool
    if _pool is None:
        _pool = ConnectionPool(
            conninfo or get_settings().database_url,
            min_size=1,
            max_size=20,
            # autocommit + explicit `with conn.transaction():` in every write service,
            # so data is COMMITTED before the HTTP response is sent.
            kwargs={"row_factory": dict_row, "autocommit": True},
            configure=_configure,
            open=True,
        )
    return _pool


def _configure(conn: psycopg.Connection) -> None:
    # Return every timestamp in local time (e.g. 2026-11-10T09:00:00+07:00) regardless of server settings.
    conn.execute("SELECT set_config('TimeZone', %s, false)", (get_settings().timezone,))


def close_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None


def get_conn() -> Iterator[psycopg.Connection]:
    """FastAPI dependency: borrow one pooled connection for the request.

    Write services wrap their work in `with conn.transaction():`, which COMMITs
    on success and ROLLs BACK if any business rule raises, so a failed request
    never leaves half-written data behind.
    """
    pool = init_pool()
    with pool.connection() as conn:
        yield conn


def apply_schema(conninfo: str | None = None) -> None:
    sql = (Path(__file__).parent / "schema.sql").read_text(encoding="utf-8")
    with psycopg.connect(conninfo or get_settings().database_url, autocommit=True) as conn:
        conn.execute(sql)
        # Runs on its own because a new enum value can't be used in the transaction that adds it.
        conn.execute("ALTER TYPE booking_status ADD VALUE IF NOT EXISTS 'NO_SHOW'")
