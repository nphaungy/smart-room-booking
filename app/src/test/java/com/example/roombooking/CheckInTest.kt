package com.example.roombooking

import com.example.roombooking.data.remote.BookingDto
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.domain.CheckInCode
import com.example.roombooking.domain.toDomain
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CheckInTest {
    private val base = """
        "id": 1, "room_id": 1, "room_code": "SC-301", "room_name": "ห้อง", "building": "ตึก",
        "booker_id": 3, "booker_name": "ผู้จอง", "start_time": "2026-11-10T09:00:00+07:00",
        "end_time": "2026-11-10T10:00:00+07:00", "attendees": 5, "purpose": "ประชุม",
        "created_at": "2026-11-01T09:00:00+07:00", "updated_at": "2026-11-01T09:00:00+07:00", "version": 1,
        "can_edit": false, "can_cancel": false, "can_decide": false
    """

    @Test fun `QR content round-trips`() {
        assertEquals("AB12CD34EF", CheckInCode.parse(CheckInCode.qrContent("AB12CD34EF")))
    }

    @Test fun `typed code tolerates case and spaces`() {
        assertEquals("AB12CD34EF", CheckInCode.parse("  ab12cd34ef "))
    }

    @Test fun `other QR codes and bad input are rejected`() {
        listOf("", "https://example.com", "ABC", "AB12CD34EF00", "GGGGGGGGGG", "roombooking://checkin?code=").forEach {
            assertNull(it, CheckInCode.parse(it))
        }
    }

    @Test fun `no-show status is understood`() {
        val b = Gson().fromJson("""{$base, "status": "NO_SHOW"}""", BookingDto::class.java).toDomain()
        assertEquals(BookingStatus.NO_SHOW, b.status)
        assertFalse(b.status.isActive)
    }

    /** A booking cached on the phone before this feature has none of the check-in fields. */
    @Test fun `booking cached before check-in still loads`() {
        val b = Gson().fromJson("""{$base, "status": "APPROVED"}""", BookingDto::class.java).toDomain()
        assertNull(b.checkedInAt)
        assertFalse(b.canCheckIn)
        assertFalse(b.canStaffCheckIn)
    }

    @Test fun `check-in fields are mapped`() {
        val b = Gson().fromJson(
            """{$base, "status": "APPROVED", "checked_in_at": "2026-11-10T08:55:00+07:00",
                "checkin_method": "STAFF", "can_check_in": true}""",
            BookingDto::class.java,
        ).toDomain()
        assertEquals(8, b.checkedInAt?.hour)
        assertEquals(true, b.checkedInByStaff)
        assertEquals(true, b.canCheckIn)
    }
}
