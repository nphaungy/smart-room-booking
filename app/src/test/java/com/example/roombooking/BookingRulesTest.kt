package com.example.roombooking

import com.example.roombooking.data.remote.BusySlotDto
import com.example.roombooking.data.remote.ScheduleDto
import com.example.roombooking.data.remote.parseHttpError
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.SlotKind
import com.example.roombooking.domain.toDomain
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRulesTest {
    private val tomorrow = BookingRules.today().plusDays(1)

    @Test fun `valid input passes`() {
        assertNull(BookingRules.validate(tomorrow, 9 * 60, 10 * 60, 10, 40, "ประชุม"))
    }

    @Test fun `BR01 start must be before end`() {
        assertEquals("เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด", BookingRules.validate(tomorrow, 600, 600, 10, 40, "x"))
        assertEquals("เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด", BookingRules.validate(tomorrow, 660, 600, 10, 40, "x"))
    }

    @Test fun `BR07 capacity`() {
        assertTrue(BookingRules.validate(tomorrow, 540, 600, 41, 40, "x")!!.contains("ความจุ"))
    }

    @Test fun `BR08 past time rejected`() {
        val yesterday = BookingRules.today().minusDays(1)
        assertEquals("ไม่สามารถจองเวลาที่ผ่านมาแล้ว", BookingRules.validate(yesterday, 540, 600, 1, 40, "x"))
    }

    @Test fun `purpose required`() {
        assertEquals("กรุณาระบุวัตถุประสงค์", BookingRules.validate(tomorrow, 540, 600, 1, 40, "   "))
    }

    @Test fun `time options are 30-minute steps within opening hours`() {
        assertEquals(8 * 60, BookingRules.timeOptions.first())
        assertEquals(20 * 60, BookingRules.timeOptions.last())
        assertEquals(25, BookingRules.timeOptions.size)
    }

    @Test fun `api format carries Bangkok offset and seconds`() {
        assertEquals("2026-11-10T09:30:00+07:00",
            BookingRules.toApi(java.time.LocalDate.of(2026, 11, 10), 9 * 60 + 30))
    }

    @Test fun `schedule is split into 30-minute slots`() {
        val dto = ScheduleDto(
            roomId = 1, date = "2026-11-10",
            openTime = "2026-11-10T08:00:00+07:00", closeTime = "2026-11-10T20:00:00+07:00", slotMinutes = 30,
            busy = listOf(
                BusySlotDto("2026-11-10T09:00:00+07:00", "2026-11-10T10:30:00+07:00", "BOOKING", "APPROVED", 5, true, "x"),
                BusySlotDto("2026-11-10T13:00:00+07:00", "2026-11-10T14:00:00+07:00", "CLOSURE", null, null, false, "ซ่อม"),
            ),
            free = emptyList(),
        )
        val slots = dto.toDomain().slots
        assertEquals(24, slots.size)
        assertEquals(SlotKind.FREE, slots[1].kind)              // 08:30
        assertEquals(SlotKind.APPROVED, slots[2].kind)          // 09:00
        assertEquals(SlotKind.APPROVED, slots[4].kind)          // 10:00
        assertEquals(SlotKind.FREE, slots[5].kind)              // 10:30 (end is exclusive)
        assertEquals(SlotKind.CLOSURE, slots[10].kind)          // 13:00
        assertTrue(slots[2].isMine)
    }

    @Test fun `server error body is parsed`() {
        val body = """{"error":{"code":"BOOKING_CONFLICT","message":"ช่วงเวลานี้ถูกจองแล้ว","details":{}}}"""
        val e = parseHttpError(Gson(), 409, body)
        assertEquals("BOOKING_CONFLICT", e.code)
        assertEquals("ช่วงเวลานี้ถูกจองแล้ว", e.message)
        assertEquals(409, e.httpStatus)
    }

    @Test fun `non-json error falls back to a Thai message`() {
        val e = parseHttpError(Gson(), 503, "<html>Bad gateway</html>")
        assertEquals("เซิร์ฟเวอร์ขัดข้อง กรุณาลองใหม่ภายหลัง", e.message)
    }
}
