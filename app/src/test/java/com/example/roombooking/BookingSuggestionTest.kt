package com.example.roombooking

import com.example.roombooking.data.remote.BookingDto
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.ScheduleSlot
import com.example.roombooking.domain.SlotKind
import com.example.roombooking.domain.isCheckInOpen
import com.example.roombooking.domain.toDomain
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

class BookingSuggestionTest {
    private val day = LocalDate.of(2026, 11, 10)

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime = day.atTime(hour, minute).atZone(BookingRules.ZONE)

    /** One 30-minute row per [kinds] entry, starting at 08:00. */
    private fun slots(vararg kinds: SlotKind) = kinds.mapIndexed { i, kind ->
        val start = at(8).plusMinutes(30L * i).toOffsetDateTime()
        ScheduleSlot(start, start.plusMinutes(30), kind, null, null, false)
    }

    @Test fun `today while a slot can still start`() {
        assertEquals(day, BookingRules.firstBookableDate(at(9)))
        assertEquals(day, BookingRules.firstBookableDate(at(19, 29)))
    }

    @Test fun `tomorrow once the last slot has begun`() {
        assertEquals(day.plusDays(1), BookingRules.firstBookableDate(at(19, 30)))
        assertEquals(day.plusDays(1), BookingRules.firstBookableDate(at(21, 23)))
    }

    @Test fun `suggests an hour at the first free time`() {
        val s = slots(SlotKind.APPROVED, SlotKind.FREE, SlotKind.FREE, SlotKind.FREE)
        assertEquals(8 * 60 + 30 to 9 * 60 + 30, BookingRules.suggestSlot(s, at(7).toOffsetDateTime()))
    }

    @Test fun `stops at the next booking`() {
        val s = slots(SlotKind.FREE, SlotKind.PENDING, SlotKind.FREE)
        assertEquals(8 * 60 to 8 * 60 + 30, BookingRules.suggestSlot(s, at(7).toOffsetDateTime()))
    }

    @Test fun `skips a free slot that has already started`() {
        val s = slots(SlotKind.FREE, SlotKind.FREE)
        assertEquals(8 * 60 + 30 to 9 * 60, BookingRules.suggestSlot(s, at(8, 10).toOffsetDateTime()))
    }

    @Test fun `no suggestion when the day is full or over`() {
        assertNull(BookingRules.suggestSlot(slots(SlotKind.APPROVED, SlotKind.CLOSURE), at(7).toOffsetDateTime()))
        assertNull(BookingRules.suggestSlot(slots(SlotKind.FREE), at(21).toOffsetDateTime()))
    }

    private fun booking(extra: String) = Gson().fromJson(
        """{"id": 1, "room_id": 1, "room_code": "R", "room_name": "r", "building": "b", "booker_id": 3,
            "booker_name": "n", "start_time": "2026-11-10T09:00:00+07:00", "end_time": "2026-11-10T10:00:00+07:00",
            "attendees": 1, "purpose": "p", "status": "APPROVED", "created_at": "2026-11-01T09:00:00+07:00",
            "updated_at": "2026-11-01T09:00:00+07:00", "version": 1, "can_edit": false, "can_cancel": false,
            "can_decide": false$extra}""",
        BookingDto::class.java,
    ).toDomain()

    private val window = """, "checkin_opens_at": "2026-11-10T08:45:00+07:00", "checkin_deadline": "2026-11-10T09:15:00+07:00""""

    @Test fun `check-in follows the clock, not the cached flag`() {
        val b = booking(window) // can_check_in is absent, as on a booking cached before the window opened
        assertFalse(b.canCheckIn)
        assertFalse(b.isCheckInOpen(OffsetDateTime.parse("2026-11-10T08:44:59+07:00")))
        assertTrue(b.isCheckInOpen(OffsetDateTime.parse("2026-11-10T08:45:00+07:00")))
        assertTrue(b.isCheckInOpen(OffsetDateTime.parse("2026-11-10T09:15:00+07:00")))
        assertFalse(b.isCheckInOpen(OffsetDateTime.parse("2026-11-10T09:15:01+07:00")))
    }

    @Test fun `no check-in when the server sent no window`() {
        assertFalse(booking("").isCheckInOpen(OffsetDateTime.parse("2026-11-10T09:00:00+07:00")))
    }
}
