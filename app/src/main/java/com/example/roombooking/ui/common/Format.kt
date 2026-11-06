package com.example.roombooking.ui.common

import com.example.roombooking.domain.BookingRules
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val thai: Locale = Locale.forLanguageTag("th-TH")
    private val dateLongF = DateTimeFormatter.ofPattern("EEE d MMM yyyy", thai)
    private val dateShortF = DateTimeFormatter.ofPattern("d MMM", thai)
    private val dayNameF = DateTimeFormatter.ofPattern("EEE", thai)
    private val monthYearF = DateTimeFormatter.ofPattern("MMMM yyyy", thai)
    private val timeF = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeF = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", thai)

    fun date(d: LocalDate): String = d.format(dateLongF)
    fun dateShort(d: LocalDate): String = d.format(dateShortF)
    fun dayName(d: LocalDate): String = d.format(dayNameF)
    fun monthYear(d: LocalDate): String = d.format(monthYearF)

    private fun local(dt: OffsetDateTime) = dt.atZoneSameInstant(BookingRules.ZONE)

    fun date(dt: OffsetDateTime): String = local(dt).format(dateLongF)
    fun time(dt: OffsetDateTime): String = local(dt).format(timeF)
    fun dateTime(dt: OffsetDateTime): String = local(dt).format(dateTimeF)
    fun range(start: OffsetDateTime, end: OffsetDateTime): String = "${time(start)}–${time(end)} น."
    fun bookingWhen(start: OffsetDateTime, end: OffsetDateTime): String = "${date(start)}  ${range(start, end)}"
}
