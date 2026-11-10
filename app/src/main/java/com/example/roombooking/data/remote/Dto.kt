package com.example.roombooking.data.remote

import com.google.gson.annotations.SerializedName

// JSON shapes of the REST API (backend/openapi.json). Timestamps stay as ISO-8601
// strings here and are parsed into java.time objects by the mappers in domain/Mappers.kt.

// ---------------------------------------------------------------- auth
data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("full_name") val fullName: String,
    val faculty: String?,
)

data class UserDto(
    val id: Long,
    val email: String,
    @SerializedName("full_name") val fullName: String,
    val role: String,
    val faculty: String?,
)

data class TokenDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: UserDto,
)

data class RoleRequest(val role: String)

// ---------------------------------------------------------------- rooms
data class RoomEquipmentDto(val id: Int, val name: String, val quantity: Int)

data class EquipmentDto(val id: Int, val name: String)

data class RoomTypeDto(val id: Int, val name: String)

data class RoomDto(
    val id: Long,
    val code: String,
    val name: String,
    val building: String,
    val floor: Int,
    val capacity: Int,
    @SerializedName("type_id") val typeId: Int,
    @SerializedName("type_name") val typeName: String,
    val status: String,
    val description: String?,
    @SerializedName("image_url") val imageUrl: String?,
    val equipment: List<RoomEquipmentDto>?,
)

data class RoomEquipmentRequest(
    @SerializedName("equipment_id") val equipmentId: Int,
    val quantity: Int,
)

data class RoomRequest(
    val code: String,
    val name: String,
    val building: String,
    val floor: Int,
    val capacity: Int,
    @SerializedName("type_id") val typeId: Int,
    val status: String,
    val description: String?,
    @SerializedName("image_url") val imageUrl: String?,
    val equipment: List<RoomEquipmentRequest>,
)

data class NameRequest(val name: String)

data class BusySlotDto(
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val kind: String,
    val status: String?,
    @SerializedName("booking_id") val bookingId: Long?,
    @SerializedName("is_mine") val isMine: Boolean,
    val label: String,
)

data class FreeSlotDto(
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
)

data class ScheduleDto(
    @SerializedName("room_id") val roomId: Long,
    val date: String,
    @SerializedName("open_time") val openTime: String,
    @SerializedName("close_time") val closeTime: String,
    @SerializedName("slot_minutes") val slotMinutes: Int,
    val busy: List<BusySlotDto>,
    val free: List<FreeSlotDto>,
)

data class ClosureRequest(
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val reason: String,
)

data class ClosureDto(
    val id: Long,
    @SerializedName("room_id") val roomId: Long,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val reason: String,
)

// ---------------------------------------------------------------- bookings
data class BookingRequest(
    @SerializedName("room_id") val roomId: Long,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val attendees: Int,
    val purpose: String,
    val version: Int? = null, // only for PUT (optimistic lock); Gson omits nulls
)

data class RejectRequest(val reason: String)

data class CancelRequest(val note: String?)

data class StatusLogDto(
    @SerializedName("from_status") val fromStatus: String?,
    @SerializedName("to_status") val toStatus: String,
    @SerializedName("changed_by_name") val changedByName: String,
    @SerializedName("changed_at") val changedAt: String,
    val note: String?,
)

data class BookingDto(
    val id: Long,
    @SerializedName("room_id") val roomId: Long,
    @SerializedName("room_code") val roomCode: String,
    @SerializedName("room_name") val roomName: String,
    val building: String,
    @SerializedName("booker_id") val bookerId: Long,
    @SerializedName("booker_name") val bookerName: String,
    @SerializedName("approver_id") val approverId: Long?,
    @SerializedName("approver_name") val approverName: String?,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val attendees: Int,
    val purpose: String,
    val status: String,
    @SerializedName("reject_reason") val rejectReason: String?,
    @SerializedName("decided_at") val decidedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val version: Int,
    @SerializedName("can_edit") val canEdit: Boolean,
    @SerializedName("can_cancel") val canCancel: Boolean,
    @SerializedName("can_decide") val canDecide: Boolean,
    // Nullable or false when absent, so bookings cached before check-in existed still load.
    @SerializedName("checked_in_at") val checkedInAt: String?,
    @SerializedName("checkin_method") val checkinMethod: String?,
    @SerializedName("checkin_opens_at") val checkinOpensAt: String?,
    @SerializedName("checkin_deadline") val checkinDeadline: String?,
    // The booker's e-ticket for check-in. Only the owner of an APPROVED booking receives it.
    @SerializedName("ticket_code") val ticketCode: String?,
    @SerializedName("can_staff_check_in") val canStaffCheckIn: Boolean,
    val logs: List<StatusLogDto>?, // only present on GET /bookings/{id}
)

// ---------------------------------------------------------------- notifications / dashboard
data class NotificationDto(
    val id: Long,
    @SerializedName("booking_id") val bookingId: Long?,
    val type: String,
    val title: String,
    val message: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class StatusCountDto(val status: String, val count: Int)

data class RoomUsageDto(
    @SerializedName("room_id") val roomId: Long,
    @SerializedName("room_code") val roomCode: String,
    @SerializedName("room_name") val roomName: String,
    @SerializedName("approved_bookings") val approvedBookings: Int,
    @SerializedName("approved_hours") val approvedHours: Double,
    @SerializedName("utilization_percent") val utilizationPercent: Double,
)

data class HourCountDto(val hour: Int, val count: Int)

data class DashboardDto(
    @SerializedName("date_from") val dateFrom: String,
    @SerializedName("date_to") val dateTo: String,
    @SerializedName("total_bookings") val totalBookings: Int,
    @SerializedName("pending_now") val pendingNow: Int,
    @SerializedName("by_status") val byStatus: List<StatusCountDto>,
    @SerializedName("approval_rate_percent") val approvalRatePercent: Double?,
    val rooms: List<RoomUsageDto>,
    @SerializedName("by_hour") val byHour: List<HourCountDto>,
)

// ---------------------------------------------------------------- check-in
data class TicketScanRequest(val code: String)

data class StaffCheckInRequest(val note: String)

// ---------------------------------------------------------------- waitlist
data class WaitlistEntryDto(
    val id: Long,
    @SerializedName("room_code") val roomCode: String,
    @SerializedName("room_name") val roomName: String,
    val building: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    val status: String,
    val position: Int?,
    @SerializedName("booking_id") val bookingId: Long?,
)

// ---------------------------------------------------------------- monthly report
data class ReportSummaryDto(
    @SerializedName("total_requests") val totalRequests: Int,
    val approved: Int,
    val pending: Int,
    val rejected: Int,
    val cancelled: Int,
    @SerializedName("checked_in") val checkedIn: Int,
    @SerializedName("no_shows") val noShows: Int,
    @SerializedName("attendance_rate_percent") val attendanceRatePercent: Double?,
    @SerializedName("booked_hours") val bookedHours: Double,
    @SerializedName("used_hours") val usedHours: Double,
)

data class ReportRoomDto(
    @SerializedName("room_code") val roomCode: String,
    @SerializedName("room_name") val roomName: String,
    val approved: Int,
    @SerializedName("checked_in") val checkedIn: Int,
    @SerializedName("no_shows") val noShows: Int,
    @SerializedName("booked_hours") val bookedHours: Double,
    @SerializedName("used_hours") val usedHours: Double,
    @SerializedName("utilization_percent") val utilizationPercent: Double,
)

data class ReportNoShowUserDto(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("full_name") val fullName: String,
    val email: String,
    @SerializedName("no_shows") val noShows: Int,
)

data class ReportWaitlistDto(val joined: Int, val waiting: Int, val promoted: Int, val cancelled: Int, val expired: Int)

data class MonthlyReportDto(
    val summary: ReportSummaryDto,
    val rooms: List<ReportRoomDto>,
    @SerializedName("no_show_users") val noShowUsers: List<ReportNoShowUserDto>,
    val waitlist: ReportWaitlistDto,
)

// ---------------------------------------------------------------- errors
data class ErrorEnvelope(val error: ErrorDetail?)

data class ErrorDetail(val code: String?, val message: String?, val details: Map<String, Any>?)
