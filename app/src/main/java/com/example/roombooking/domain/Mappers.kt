package com.example.roombooking.domain

import com.example.roombooking.data.remote.BookingDto
import com.example.roombooking.data.remote.ClosureDto
import com.example.roombooking.data.remote.DashboardDto
import com.example.roombooking.data.remote.MonthlyReportDto
import com.example.roombooking.data.remote.NotificationDto
import com.example.roombooking.data.remote.RoomCheckInCodeDto
import com.example.roombooking.data.remote.RoomDto
import com.example.roombooking.data.remote.ScheduleDto
import com.example.roombooking.data.remote.UserDto
import com.example.roombooking.data.remote.WaitlistEntryDto
import java.time.LocalDate
import java.time.OffsetDateTime

fun parseTime(s: String): OffsetDateTime = OffsetDateTime.parse(s)

fun UserDto.toDomain() = User(id, email, fullName, Role.valueOf(role), faculty)

fun RoomDto.toDomain() = Room(
    id = id, code = code, name = name, building = building, floor = floor, capacity = capacity,
    typeId = typeId, typeName = typeName, active = status == "ACTIVE", description = description,
    equipment = equipment.orEmpty().map { Equipment(it.id, it.name, it.quantity) },
)

fun BookingDto.toDomain() = Booking(
    id = id, roomId = roomId, roomCode = roomCode, roomName = roomName, building = building,
    bookerId = bookerId, bookerName = bookerName, approverName = approverName,
    start = parseTime(startTime), end = parseTime(endTime), attendees = attendees, purpose = purpose,
    status = BookingStatus.from(status) ?: BookingStatus.PENDING, rejectReason = rejectReason,
    decidedAt = decidedAt?.let(::parseTime), createdAt = parseTime(createdAt), version = version,
    canEdit = canEdit, canCancel = canCancel, canDecide = canDecide,
    checkedInAt = checkedInAt?.let(::parseTime), checkedInByStaff = checkinMethod == "STAFF",
    checkinOpensAt = checkinOpensAt?.let(::parseTime), checkinDeadline = checkinDeadline?.let(::parseTime),
    canCheckIn = canCheckIn, canStaffCheckIn = canStaffCheckIn,
    logs = logs.orEmpty().map {
        StatusLog(
            from = BookingStatus.from(it.fromStatus),
            to = BookingStatus.from(it.toStatus) ?: BookingStatus.PENDING,
            changedBy = it.changedByName, changedAt = parseTime(it.changedAt), note = it.note,
        )
    },
)

fun WaitlistEntryDto.toDomain() = WaitlistEntry(
    id = id, roomCode = roomCode, roomName = roomName, building = building,
    start = parseTime(startTime), end = parseTime(endTime),
    status = WaitlistStatus.entries.firstOrNull { it.name == status } ?: WaitlistStatus.EXPIRED,
    position = position, bookingId = bookingId,
)

fun RoomCheckInCodeDto.toDomain() = RoomCheckInCode(roomCode, roomName, checkinCode)

fun MonthlyReportDto.toDomain() = MonthlyReport(
    totalRequests = summary.totalRequests, approved = summary.approved, pending = summary.pending,
    rejected = summary.rejected, cancelled = summary.cancelled, checkedIn = summary.checkedIn,
    noShows = summary.noShows, attendanceRatePercent = summary.attendanceRatePercent,
    bookedHours = summary.bookedHours, usedHours = summary.usedHours,
    rooms = rooms.map {
        ReportRoom(it.roomCode, it.roomName, it.approved, it.checkedIn, it.noShows, it.bookedHours, it.usedHours, it.utilizationPercent)
    },
    noShowUsers = noShowUsers.map { ReportNoShowUser(it.userId, it.fullName, it.email, it.noShows) },
    waitlist = ReportWaitlist(waitlist.joined, waitlist.waiting, waitlist.promoted, waitlist.cancelled, waitlist.expired),
)

fun ClosureDto.toDomain() = Closure(id, roomId, parseTime(startTime), parseTime(endTime), reason)

fun NotificationDto.toDomain() = AppNotification(id, bookingId, type, title, message, isRead, parseTime(createdAt))

fun DashboardDto.toDomain() = Dashboard(
    from = LocalDate.parse(dateFrom), to = LocalDate.parse(dateTo),
    totalBookings = totalBookings, pendingNow = pendingNow,
    byStatus = byStatus.mapNotNull { s -> BookingStatus.from(s.status)?.let { it to s.count } }.toMap(),
    approvalRatePercent = approvalRatePercent,
    rooms = rooms.map { RoomUsage(it.roomCode, it.roomName, it.approvedBookings, it.approvedHours, it.utilizationPercent) },
    byHour = byHour.associate { it.hour to it.count },
)

/**
 * Turns the server's busy/free intervals into fixed 30-minute rows so the calendar
 * can draw one line per slot (free, pending, approved, closed).
 */
fun ScheduleDto.toDomain(): RoomSchedule {
    val open = parseTime(openTime)
    val close = parseTime(closeTime)
    val busyList = busy.map { b ->
        val kind = when {
            b.kind == "CLOSURE" -> SlotKind.CLOSURE
            b.status == "APPROVED" -> SlotKind.APPROVED
            else -> SlotKind.PENDING
        }
        Triple(parseTime(b.startTime), parseTime(b.endTime), b) to kind
    }
    val slots = mutableListOf<ScheduleSlot>()
    var cursor = open
    while (cursor.isBefore(close)) {
        val next = cursor.plusMinutes(slotMinutes.toLong())
        val hit = busyList.firstOrNull { (t, _) -> t.first.isBefore(next) && cursor.isBefore(t.second) }
        slots += if (hit == null) {
            ScheduleSlot(cursor, next, SlotKind.FREE, null, null, false)
        } else {
            val (t, kind) = hit
            ScheduleSlot(cursor, next, kind, t.third.label, t.third.bookingId, t.third.isMine)
        }
        cursor = next
    }
    return RoomSchedule(roomId, LocalDate.parse(date), slotMinutes, slots)
}
