from contextlib import asynccontextmanager

from fastapi import FastAPI

from .db import apply_schema, close_pool, init_pool
from .errors import install_error_handlers
from .routers import admin, auth, bookings, misc, rooms


@asynccontextmanager
async def lifespan(_: FastAPI):
    apply_schema()   # CREATE ... IF NOT EXISTS: safe on every start
    init_pool()
    yield
    close_pool()


app = FastAPI(
    title="Smart Room Booking API",
    version="1.0.0",
    description="REST API สำหรับแอป Android Smart Room Booking (RB01–RB10)",
    lifespan=lifespan,
)
install_error_handlers(app)

for r in (auth.router, rooms.router, bookings.router, misc.router, admin.router):
    app.include_router(r, prefix="/api")


@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok"}
