package com.example.roombooking.domain

/**
 * The booker's check-in e-ticket. Every booking has its own 12-character code; the booker's app shows it
 * as a QR (like a cinema or concert ticket) and staff scan it at the room, or type the code under it.
 */
object CheckInCode {
    private const val QR_PREFIX = "roombooking://ticket/"
    private val FORMAT = Regex("^[0-9A-F]{12}$")

    fun qrContent(code: String): String = QR_PREFIX + code

    /** "ABCD1234EF56" -> "ABCD-1234-EF56", easier to read out or type. The dashes are ignored when parsed. */
    fun display(code: String): String = code.chunked(4).joinToString("-")

    /** Returns the code from scanned or typed text, or null if it isn't one of this app's tickets. */
    fun parse(raw: String): String? {
        var text = raw.trim()
        if (text.startsWith(QR_PREFIX, ignoreCase = true)) text = text.substring(QR_PREFIX.length)
        return text.replace(" ", "").replace("-", "").uppercase().takeIf(FORMAT::matches)
    }
}
