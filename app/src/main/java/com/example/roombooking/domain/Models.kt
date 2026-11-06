package com.example.roombooking.domain

import java.time.LocalDate
import java.time.OffsetDateTime

enum class Role { BOOKER, APPROVER, ADMIN;

    val isStaff: Boolean get() = this == APPROVER || this == ADMIN
}

/** RB06: the four booking statuses. */
enum class BookingStatus(val thai: String) {
    PENDING("รออนุมัติ"),
    APPROVED("อนุมัติแล้ว"),
    REJECTED("ถูกปฏิเสธ"),
    CANCELLED("ยกเลิกแล้ว");

    val isActive: Boolean get() = this == PENDING || this == APPROVED

    companion object {
        fun from(value: String?): BookingStatus? = entries.firstOrNull { it.name == value }
    }
}

data class User(
    val id: Long,
    val email: String,
    val fullName: String,
    val role: Role,
    val faculty: String?,
)

data class Equipment(val id: Int, val name: String, val quantity: Int = 1)

data class RoomType(val id: Int, val name: String)

data class Room(
    val id: Long,
    val code: String,
    val name: String,
    val building: String,
    val floor: Int,
    val capacity: Int,
    val typeId: Int,
    val typeName: String,
    val active: Boolean,
    val description: String?,
    val equipment: List<Equipment>,
)

enum class SlotKind { FREE, PENDING, APPROVED, CLOSURE }

/** One 30-minute row in the room calendar (RB04). */
data class ScheduleSlot(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val kind: SlotKind,
    val label: String?,
    val bookingId: Long?,
    val isMine: Boolean,
)

data class RoomSchedule(
    val roomId: Long,
    val date: LocalDate,
    val slotMinutes: Int,
    val slots: List<ScheduleSlot>,
)

data class StatusLog(
    val from: BookingStatus?,
    val to: BookingStatus,
    val changedBy: String,
    val changedAt: OffsetDateTime,
    val note: String?,
)

data class Booking(
    val id: Long,
    val roomId: Long,
    val roomCode: String,
    val roomName: String,
    val building: String,
    val bookerId: Long,
    val bookerName: String,
    val approverName: String?,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val attendees: Int,
    val purpose: String,
    val status: BookingStatus,
    val rejectReason: String?,
    val decidedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val version: Int,
    val canEdit: Boolean,
    val canCancel: Boolean,
    val canDecide: Boolean,
    val logs: List<StatusLog> = emptyList(),
)

data class Closure(val id: Long, val roomId: Long, val start: OffsetDateTime, val end: OffsetDateTime, val reason: String)

data class AppNotification(
    val id: Long,
    val bookingId: Long?,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: OffsetDateTime,
)

data class RoomUsage(val roomCode: String, val roomName: String, val approvedBookings: Int,
                     val approvedHours: Double, val utilizationPercent: Double)

data class Dashboard(
    val from: LocalDate,
    val to: LocalDate,
    val totalBookings: Int,
    val pendingNow: Int,
    val byStatus: Map<BookingStatus, Int>,
    val approvalRatePercent: Double?,
    val rooms: List<RoomUsage>,
    val byHour: Map<Int, Int>,
)

/** Search filters for RB03. start/end null = list all rooms. */
data class RoomSearch(
    val date: LocalDate? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val attendees: Int? = null,
    val typeId: Int? = null,
    val query: String? = null,
) {
    val hasTimeRange: Boolean get() = date != null && startMinute != null && endMinute != null
}
