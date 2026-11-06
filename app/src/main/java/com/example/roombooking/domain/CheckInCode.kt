package com.example.roombooking.domain

/** The code on a room's door, carried inside its QR or typed by hand. */
object CheckInCode {
    private const val QR_PREFIX = "roombooking://checkin?code="
    private val FORMAT = Regex("^[0-9A-F]{10}$")

    fun qrContent(code: String): String = QR_PREFIX + code

    /** Returns the code from scanned or typed text, or null if it isn't a check-in code. */
    fun parse(raw: String): String? =
        raw.trim().removePrefix(QR_PREFIX).trim().uppercase().takeIf(FORMAT::matches)
}
