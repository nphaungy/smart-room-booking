package com.example.roombooking.data.repository

import com.example.roombooking.data.SessionManager
import com.example.roombooking.data.local.BookingDao
import com.example.roombooking.data.local.BookingEntity
import com.example.roombooking.data.remote.ApiService
import com.example.roombooking.data.remote.BookingDto
import com.example.roombooking.data.remote.BookingRequest
import com.example.roombooking.data.remote.CancelRequest
import com.example.roombooking.data.remote.CheckInRequest
import com.example.roombooking.data.remote.RejectRequest
import com.example.roombooking.data.remote.StaffCheckInRequest
import com.example.roombooking.data.remote.apiCall
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.domain.WaitlistEntry
import com.example.roombooking.domain.toDomain
import com.example.roombooking.notification.ReminderScheduler
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BookingRepository(
    private val api: ApiService,
    private val dao: BookingDao,
    private val session: SessionManager,
    private val reminders: ReminderScheduler,
    private val gson: Gson,
) {
    /** RB09 history for the signed-in user, from the local cache. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val myBookings: Flow<List<Booking>> = session.state.flatMapLatest {
        val user = session.currentUser ?: return@flatMapLatest flowOf(emptyList<Booking>())
        dao.observeMine(user.id).map { rows -> rows.map { gson.fromJson(it.json, BookingDto::class.java).toDomain() } }
    }

    suspend fun refreshMine(): Result<Unit> = apiCall(gson) {
        val user = session.currentUser ?: return@apiCall
        val list = api.myBookings()
        dao.replaceFor(user.id, list.map { BookingEntity(it.id, user.id, gson.toJson(it), it.startTime) })
        reminders.sync(list.map { it.toDomain() })  // RB08: reminder before each approved booking
    }

    suspend fun create(roomId: Long, date: LocalDate, start: Int, end: Int, attendees: Int, purpose: String): Result<Booking> =
        apiCall(gson) {
            api.createBooking(
                BookingRequest(roomId, BookingRules.toApi(date, start), BookingRules.toApi(date, end), attendees, purpose.trim()),
            ).toDomain()
        }.onSuccess { refreshMine() }

    suspend fun update(booking: Booking, date: LocalDate, start: Int, end: Int, attendees: Int, purpose: String): Result<Booking> =
        apiCall(gson) {
            api.updateBooking(
                booking.id,
                BookingRequest(booking.roomId, BookingRules.toApi(date, start), BookingRules.toApi(date, end),
                    attendees, purpose.trim(), version = booking.version),
            ).toDomain()
        }.onSuccess { refreshMine() }

    suspend fun detail(id: Long): Result<Booking> = apiCall(gson) { api.booking(id).toDomain() }

    suspend fun cancel(id: Long, note: String?): Result<Booking> =
        apiCall(gson) { api.cancelBooking(id, CancelRequest(note?.ifBlank { null })).toDomain() }
            .onSuccess { refreshMine() }

    suspend fun checkIn(id: Long, code: String): Result<Booking> =
        apiCall(gson) { api.checkIn(id, CheckInRequest(code)).toDomain() }.onSuccess { refreshMine() }

    suspend fun staffCheckIn(id: Long, note: String): Result<Booking> =
        apiCall(gson) { api.staffCheckIn(id, StaffCheckInRequest(note.trim())).toDomain() }

    suspend fun joinWaitlist(roomId: Long, date: LocalDate, start: Int, end: Int, attendees: Int, purpose: String): Result<WaitlistEntry> =
        apiCall(gson) {
            api.joinWaitlist(
                BookingRequest(roomId, BookingRules.toApi(date, start), BookingRules.toApi(date, end), attendees, purpose.trim()),
            ).toDomain()
        }

    suspend fun myWaitlist(): Result<List<WaitlistEntry>> = apiCall(gson) { api.myWaitlist().map { it.toDomain() } }

    suspend fun leaveWaitlist(id: Long): Result<WaitlistEntry> = apiCall(gson) { api.leaveWaitlist(id).toDomain() }

    // RB07 (approver/admin)
    suspend fun pendingQueue(): Result<List<Booking>> =
        apiCall(gson) { api.allBookings(status = BookingStatus.PENDING.name).map { it.toDomain() } }

    suspend fun allBookings(status: BookingStatus?): Result<List<Booking>> =
        apiCall(gson) { api.allBookings(status = status?.name).map { it.toDomain() } }

    suspend fun approve(id: Long): Result<Booking> = apiCall(gson) { api.approveBooking(id).toDomain() }

    suspend fun reject(id: Long, reason: String): Result<Booking> =
        apiCall(gson) { api.rejectBooking(id, RejectRequest(reason.trim())).toDomain() }
}
