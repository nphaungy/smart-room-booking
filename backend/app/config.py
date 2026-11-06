from functools import lru_cache
from zoneinfo import ZoneInfo

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All values can be overridden with environment variables or a .env file."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql://postgres:postgres@localhost:5432/roombooking"
    jwt_secret: str = "change-me-in-production-please-use-a-long-random-string"
    jwt_expire_hours: int = 24 * 7

    # Leave empty to allow any email domain; e.g. "university.ac.th" to restrict sign-up.
    allowed_email_domain: str = ""

    # ---- Booking policy (the [assumption] values from the plan) ----
    timezone: str = "Asia/Bangkok"
    slot_minutes: int = 30           # BR08: bookings are made in 30-minute units
    open_hour: int = 8               # BR08: opening time (local)
    close_hour: int = 20             # BR08: closing time (local)
    max_advance_days: int = 30       # BR08: book at most 30 days ahead
    cancel_min_hours_before: int = 1  # BR06: owner may cancel up to 1 hour before start

    @property
    def tz(self) -> ZoneInfo:
        return ZoneInfo(self.timezone)


@lru_cache
def get_settings() -> Settings:
    return Settings()
