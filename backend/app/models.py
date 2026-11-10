from datetime import datetime
from typing import Literal

from pydantic import BaseModel, EmailStr, Field

Role = Literal["BOOKER", "APPROVER", "ADMIN"]
BookingStatus = Literal["PENDING", "APPROVED", "REJECTED", "CANCELLED", "NO_SHOW"]
WaitlistStatus = Literal["WAITING", "PROMOTED", "CANCELLED", "EXPIRED"]


# ---------------------------------------------------------------- auth
class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    full_name: str = Field(min_length=1, max_length=120)
    faculty: str | None = Field(default=None, max_length=120)


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class UserOut(BaseModel):
    id: int
    email: str
    full_name: str
    role: Role
    faculty: str | None = None


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class RoleUpdateIn(BaseModel):
    role: Role


# ---------------------------------------------------------------- rooms
class EquipmentOut(BaseModel):
    id: int
    name: str


class RoomEquipmentOut(BaseModel):
    id: int
    name: str
    quantity: int


class RoomTypeOut(BaseModel):
    id: int
    name: str


class RoomOut(BaseModel):
    id: int
    code: str
    name: str
    building: str
    floor: int
    capacity: int
    type_id: int
    type_name: str
    status: Literal["ACTIVE", "INACTIVE"]
    description: str | None = None
    image_url: str | None = None
    equipment: list[RoomEquipmentOut] = []


class RoomEquipmentIn(BaseModel):
    equipment_id: int
    quantity: int = Field(default=1, gt=0)


class RoomIn(BaseModel):
    code: str = Field(min_length=1, max_length=30)
    name: str = Field(min_length=1, max_length=120)
    building: str = Field(min_length=1, max_length=120)
    floor: int
    capacity: int = Field(gt=0)
    type_id: int
    status: Literal["ACTIVE", "INACTIVE"] = "ACTIVE"
    description: str | None = None
    image_url: str | None = None
    equipment: list[RoomEquipmentIn] = []


class NameIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class ClosureIn(BaseModel):
    start_time: datetime
    end_time: datetime
    reason: str = Field(min_length=1, max_length=300)


class ClosureOut(BaseModel):
    id: int
    room_id: int
    start_time: datetime
    end_time: datetime
    reason: str


class BusySlot(BaseModel):
    start_time: datetime
    end_time: datetime
    kind: Literal["BOOKING", "CLOSURE"]
    status: BookingStatus | None = None
    booking_id: int | None = None
    is_mine: bool = False
    label: str


class FreeSlot(BaseModel):
    start_time: datetime
    end_time: datetime


class ScheduleOut(BaseModel):
    room_id: int
    date: str
    open_time: datetime
    close_time: datetime
    slot_minutes: int
    busy: list[BusySlot]
    free: list[FreeSlot]


# ---------------------------------------------------------------- bookings
class BookingIn(BaseModel):
    room_id: int
    start_time: datetime
    end_time: datetime
    attendees: int = Field(gt=0)
    purpose: str = Field(min_length=1, max_length=500)


class BookingUpdateIn(BookingIn):
    # Optional optimistic-lock token: send the `version` you last saw.
    version: int | None = None


class RejectIn(BaseModel):
    reason: str = Field(min_length=1, max_length=500)


class CancelIn(BaseModel):
    note: str | None = Field(default=None, max_length=500)


class TicketScanIn(BaseModel):
    """What the staff's scanner read from the booker's QR, or the code typed by hand."""
    code: str = Field(min_length=1, max_length=200)


class StaffCheckInIn(BaseModel):
    note: str = Field(min_length=1, max_length=500)


class StatusLogOut(BaseModel):
    from_status: BookingStatus | None
    to_status: BookingStatus
    changed_by_name: str
    changed_at: datetime
    note: str | None = None


class BookingOut(BaseModel):
    id: int
    room_id: int
    room_code: str
    room_name: str
    building: str
    booker_id: int
    booker_name: str
    approver_id: int | None = None
    approver_name: str | None = None
    start_time: datetime
    end_time: datetime
    attendees: int
    purpose: str
    status: BookingStatus
    reject_reason: str | None = None
    decided_at: datetime | None = None
    created_at: datetime
    updated_at: datetime
    version: int
    can_edit: bool = False
    can_cancel: bool = False
    can_decide: bool = False
    checked_in_at: datetime | None = None
    checkin_method: Literal["SCAN", "STAFF", "SELF"] | None = None
    checkin_opens_at: datetime | None = None
    checkin_deadline: datetime | None = None
    # The booker's e-ticket. Sent only to the booking's owner, and only while it is APPROVED.
    ticket_code: str | None = None
    can_staff_check_in: bool = False


class BookingDetailOut(BookingOut):
    logs: list[StatusLogOut] = []


# ---------------------------------------------------------------- notifications / dashboard
class NotificationOut(BaseModel):
    id: int
    booking_id: int | None
    type: str
    title: str
    message: str
    is_read: bool
    created_at: datetime


class StatusCount(BaseModel):
    status: BookingStatus
    count: int


class RoomUsage(BaseModel):
    room_id: int
    room_code: str
    room_name: str
    approved_bookings: int
    approved_hours: float
    utilization_percent: float


class HourCount(BaseModel):
    hour: int
    count: int


class DashboardOut(BaseModel):
    date_from: str
    date_to: str
    total_bookings: int
    pending_now: int
    by_status: list[StatusCount]
    approval_rate_percent: float | None
    rooms: list[RoomUsage]
    by_hour: list[HourCount]


# ---------------------------------------------------------------- waitlist
class WaitlistEntryOut(BaseModel):
    id: int
    room_id: int
    room_code: str
    room_name: str
    building: str
    start_time: datetime
    end_time: datetime
    attendees: int
    purpose: str
    status: WaitlistStatus
    position: int | None = None  # place in the queue; only while WAITING
    booking_id: int | None = None
    created_at: datetime


# ---------------------------------------------------------------- monthly report
class ReportSummary(BaseModel):
    total_requests: int
    approved: int
    pending: int
    rejected: int
    cancelled: int
    checked_in: int
    no_shows: int
    attendance_rate_percent: float | None
    booked_hours: float
    used_hours: float


class ReportRoom(BaseModel):
    room_id: int
    room_code: str
    room_name: str
    approved: int
    checked_in: int
    no_shows: int
    booked_hours: float
    used_hours: float
    utilization_percent: float


class ReportNoShowUser(BaseModel):
    user_id: int
    full_name: str
    email: str
    faculty: str | None = None
    no_shows: int


class ReportWaitlist(BaseModel):
    joined: int
    waiting: int
    promoted: int
    cancelled: int
    expired: int


class MonthlyReportOut(BaseModel):
    year: int
    month: int
    summary: ReportSummary
    rooms: list[ReportRoom]
    no_show_users: list[ReportNoShowUser]
    waitlist: ReportWaitlist
