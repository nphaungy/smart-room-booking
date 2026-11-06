import asyncio
import contextlib
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .config import get_settings
from .db import apply_schema, close_pool, init_pool
from .errors import install_error_handlers
from .routers import admin, auth, bookings, misc, reports, rooms, waitlist
from .services.sweeper import run_sweep

log = logging.getLogger(__name__)


def _sweep_once() -> None:
    with init_pool().connection() as conn:
        run_sweep(conn)


async def _sweep_forever(interval: int) -> None:
    while True:
        try:
            await asyncio.to_thread(_sweep_once)
        except Exception:
            log.exception("Sweep failed; retrying at the next interval")
        await asyncio.sleep(interval)


@asynccontextmanager
async def lifespan(_: FastAPI):
    apply_schema()   # CREATE ... IF NOT EXISTS: safe on every start
    init_pool()
    interval = get_settings().sweep_interval_seconds
    sweeper = asyncio.create_task(_sweep_forever(interval)) if interval > 0 else None
    yield
    if sweeper:
        sweeper.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await sweeper
    close_pool()


app = FastAPI(
    title="Smart Room Booking API",
    version="1.0.0",
    description="REST API สำหรับแอป Android Smart Room Booking (RB01–RB10)",
    lifespan=lifespan,
)
install_error_handlers(app)

for r in (auth.router, rooms.router, bookings.router, waitlist.router, reports.router, misc.router, admin.router):
    app.include_router(r, prefix="/api")


@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok"}
