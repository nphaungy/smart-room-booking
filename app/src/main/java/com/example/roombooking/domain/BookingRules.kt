package com.example.roombooking.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Client-side copies of the booking policy, used only for instant feedback in forms.
 * The server re-checks everything (it is the source of truth, see backend/app/rules.py).
 */
object BookingRules {
    val ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
    const val SLOT_MINUTES = 30
    const val OPEN_HOUR = 8
    const val CLOSE_HOUR = 20
    const val MAX_ADVANCE_DAYS = 30L

    private val API_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** All selectable times of day as minutes since midnight: 08:00, 08:30 ... 20:00 */
    val timeOptions: List<Int> = (OPEN_HOUR * 60..CLOSE_HOUR * 60 step SLOT_MINUTES).toList()

    fun today(): LocalDate = LocalDate.now(ZONE)

    /** Dates a user may book: today .. today + 30 days */
    fun bookableDates(): List<LocalDate> = (0..MAX_ADVANCE_DAYS).map { today().plusDays(it) }

    fun minuteLabel(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun minuteOf(dt: OffsetDateTime): Int = dt.atZoneSameInstant(ZONE).let { it.hour * 60 + it.minute }

    fun toApi(date: LocalDate, minuteOfDay: Int): String =
        ZonedDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), ZONE).format(API_FORMAT)

    fun toApi(dt: OffsetDateTime): String = dt.atZoneSameInstant(ZONE).format(API_FORMAT)

    /** Today while a slot can still start, otherwise tomorrow. */
    fun firstBookableDate(now: ZonedDateTime = ZonedDateTime.now(ZONE)): LocalDate {
        val lastStart = LocalTime.of(CLOSE_HOUR, 0).minusMinutes(SLOT_MINUTES.toLong())
        return if (now.toLocalTime().isBefore(lastStart)) now.toLocalDate() else now.toLocalDate().plusDays(1)
    }

    /**
     * The first free time that hasn't started, as start and end minutes: an hour if the room
     * stays free that long, otherwise up to the next booking. Null when the day has none.
     */
    fun suggestSlot(slots: List<ScheduleSlot>, now: OffsetDateTime): Pair<Int, Int>? {
        val first = slots.indexOfFirst { it.kind == SlotKind.FREE && it.start.isAfter(now) }
        if (first < 0) return null
        val wanted = 60 / SLOT_MINUTES
        val free = slots.drop(first).take(wanted).takeWhile { it.kind == SlotKind.FREE }
        return minuteOf(free.first().start) to minuteOf(free.last().end)
    }

    /**
     * Returns a Thai error message, or null when the input is valid.
     * Mirrors BR01 (start < end), BR07 (capacity), BR08 (not in the past).
     */
    fun validate(date: LocalDate?, start: Int?, end: Int?, attendees: Int?, capacity: Int?, purpose: String): String? {
        if (date == null) return "กรุณาเลือกวันที่"
        if (start == null || end == null) return "กรุณาเลือกเวลาเริ่มและเวลาสิ้นสุด"
        if (start >= end) return "เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด"
        val startAt = ZonedDateTime.of(date, LocalTime.of(start / 60, start % 60), ZONE)
        if (!startAt.isAfter(ZonedDateTime.now(ZONE))) return "ไม่สามารถจองเวลาที่ผ่านมาแล้ว"
        if (attendees == null || attendees <= 0) return "กรุณาระบุจำนวนผู้เข้าร่วม"
        if (capacity != null && attendees > capacity) return "จำนวนผู้เข้าร่วมเกินความจุห้อง ($capacity คน)"
        if (purpose.isBlank()) return "กรุณาระบุวัตถุประสงค์"
        return null
    }
}
