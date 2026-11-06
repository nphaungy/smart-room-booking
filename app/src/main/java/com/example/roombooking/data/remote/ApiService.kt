package com.example.roombooking.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // RB01
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenDto

    @GET("auth/me")
    suspend fun me(): UserDto

    // RB02 / RB03: pass start+end to get only rooms that are free in that range
    @GET("rooms")
    suspend fun rooms(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("attendees") attendees: Int? = null,
        @Query("type_id") typeId: Int? = null,
        @Query("equipment_ids") equipmentIds: List<Int>? = null,
        @Query("q") q: String? = null,
    ): List<RoomDto>

    @GET("rooms/{id}")
    suspend fun room(@Path("id") id: Long): RoomDto

    // RB04
    @GET("rooms/{id}/schedule")
    suspend fun schedule(@Path("id") id: Long, @Query("date") date: String): ScheduleDto

    @GET("room-types")
    suspend fun roomTypes(): List<RoomTypeDto>

    @GET("equipment")
    suspend fun equipment(): List<EquipmentDto>

    // RB05 - RB07, RB09
    @POST("bookings")
    suspend fun createBooking(@Body body: BookingRequest): BookingDto

    @PUT("bookings/{id}")
    suspend fun updateBooking(@Path("id") id: Long, @Body body: BookingRequest): BookingDto

    @GET("bookings/me")
    suspend fun myBookings(@Query("status") status: String? = null): List<BookingDto>

    @GET("bookings")
    suspend fun allBookings(
        @Query("status") status: String? = null,
        @Query("room_id") roomId: Long? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): List<BookingDto>

    @GET("bookings/{id}")
    suspend fun booking(@Path("id") id: Long): BookingDto

    @POST("bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: Long, @Body body: CancelRequest): BookingDto

    @POST("bookings/{id}/approve")
    suspend fun approveBooking(@Path("id") id: Long): BookingDto

    @POST("bookings/{id}/reject")
    suspend fun rejectBooking(@Path("id") id: Long, @Body body: RejectRequest): BookingDto

    @POST("bookings/{id}/check-in")
    suspend fun checkIn(@Path("id") id: Long, @Body body: CheckInRequest): BookingDto

    @POST("bookings/{id}/staff-check-in")
    suspend fun staffCheckIn(@Path("id") id: Long, @Body body: StaffCheckInRequest): BookingDto

    // Waitlist
    @POST("waitlist")
    suspend fun joinWaitlist(@Body body: BookingRequest): WaitlistEntryDto

    @GET("waitlist/me")
    suspend fun myWaitlist(): List<WaitlistEntryDto>

    @POST("waitlist/{id}/cancel")
    suspend fun leaveWaitlist(@Path("id") id: Long): WaitlistEntryDto

    // Monthly report
    @GET("reports/monthly")
    suspend fun monthlyReport(@Query("year") year: Int, @Query("month") month: Int): MonthlyReportDto

    @GET("reports/monthly.csv")
    suspend fun monthlyReportCsv(@Query("year") year: Int, @Query("month") month: Int): ResponseBody

    // RB08
    @GET("notifications")
    suspend fun notifications(@Query("after_id") afterId: Long = 0): List<NotificationDto>

    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Long): Response<Unit>

    @POST("notifications/read-all")
    suspend fun markAllRead(): Response<Unit>

    // RB09
    @GET("dashboard")
    suspend fun dashboard(@Query("date_from") from: String, @Query("date_to") to: String): DashboardDto

    // RB10
    @GET("admin/rooms")
    suspend fun adminRooms(): List<RoomDto>

    @POST("admin/rooms")
    suspend fun createRoom(@Body body: RoomRequest): RoomDto

    @PUT("admin/rooms/{id}")
    suspend fun updateRoom(@Path("id") id: Long, @Body body: RoomRequest): RoomDto

    @GET("admin/rooms/{id}/checkin-code")
    suspend fun roomCheckInCode(@Path("id") roomId: Long): RoomCheckInCodeDto

    @POST("admin/rooms/{id}/checkin-code/rotate")
    suspend fun rotateRoomCheckInCode(@Path("id") roomId: Long): RoomCheckInCodeDto

    @GET("admin/rooms/{id}/closures")
    suspend fun closures(@Path("id") roomId: Long): List<ClosureDto>

    @POST("admin/rooms/{id}/closures")
    suspend fun createClosure(
        @Path("id") roomId: Long,
        @Body body: ClosureRequest,
        @Query("cancel_conflicts") cancelConflicts: Boolean = false,
    ): ClosureDto

    @DELETE("admin/closures/{id}")
    suspend fun deleteClosure(@Path("id") id: Long): Response<Unit>

    @POST("admin/equipment")
    suspend fun createEquipment(@Body body: NameRequest): EquipmentDto

    @GET("admin/users")
    suspend fun users(): List<UserDto>

    @PATCH("admin/users/{id}/role")
    suspend fun setRole(@Path("id") id: Long, @Body body: RoleRequest): UserDto
}
