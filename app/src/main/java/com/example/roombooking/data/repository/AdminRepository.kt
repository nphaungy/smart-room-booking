package com.example.roombooking.data.repository

import com.example.roombooking.data.remote.ApiService
import com.example.roombooking.data.remote.ClosureRequest
import com.example.roombooking.data.remote.NameRequest
import com.example.roombooking.data.remote.RoleRequest
import com.example.roombooking.data.remote.RoomEquipmentRequest
import com.example.roombooking.data.remote.RoomRequest
import com.example.roombooking.data.remote.apiCall
import com.example.roombooking.data.remote.orThrow
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.domain.Closure
import com.example.roombooking.domain.Dashboard
import com.example.roombooking.domain.Equipment
import com.example.roombooking.domain.MonthlyReport
import com.example.roombooking.domain.Role
import com.example.roombooking.domain.Room
import com.example.roombooking.domain.User
import com.example.roombooking.domain.toDomain
import com.google.gson.Gson
import java.time.LocalDate
import java.time.YearMonth

data class RoomForm(
    val code: String,
    val name: String,
    val building: String,
    val floor: Int,
    val capacity: Int,
    val typeId: Int,
    val active: Boolean,
    val description: String?,
    val equipment: Map<Int, Int>, // equipmentId -> quantity
)

class AdminRepository(private val api: ApiService, private val gson: Gson) {

    // RB09
    suspend fun dashboard(from: LocalDate, to: LocalDate): Result<Dashboard> =
        apiCall(gson) { api.dashboard(from.toString(), to.toString()).toDomain() }

    // Monthly report
    suspend fun monthlyReport(month: YearMonth): Result<MonthlyReport> =
        apiCall(gson) { api.monthlyReport(month.year, month.monthValue).toDomain() }

    suspend fun monthlyReportCsv(month: YearMonth): Result<ByteArray> =
        apiCall(gson) { api.monthlyReportCsv(month.year, month.monthValue).use { it.bytes() } }

    suspend fun noShows(month: YearMonth): Result<List<Booking>> = apiCall(gson) {
        api.allBookings(
            status = BookingStatus.NO_SHOW.name,
            dateFrom = month.atDay(1).toString(),
            dateTo = month.atEndOfMonth().toString(),
        ).map { it.toDomain() }
    }

    // RB10 rooms
    suspend fun rooms(): Result<List<Room>> = apiCall(gson) { api.adminRooms().map { it.toDomain() } }

    suspend fun saveRoom(id: Long?, form: RoomForm): Result<Room> = apiCall(gson) {
        val body = RoomRequest(
            code = form.code.trim(), name = form.name.trim(), building = form.building.trim(),
            floor = form.floor, capacity = form.capacity, typeId = form.typeId,
            status = if (form.active) "ACTIVE" else "INACTIVE",
            description = form.description?.trim()?.ifBlank { null }, imageUrl = null,
            equipment = form.equipment.map { (eqId, qty) -> RoomEquipmentRequest(eqId, qty) },
        )
        (if (id == null) api.createRoom(body) else api.updateRoom(id, body)).toDomain()
    }

    suspend fun addEquipment(name: String): Result<Equipment> =
        apiCall(gson) { api.createEquipment(NameRequest(name.trim())).let { Equipment(it.id, it.name) } }

    // BR03 closures
    suspend fun closures(roomId: Long): Result<List<Closure>> =
        apiCall(gson) { api.closures(roomId).map { it.toDomain() } }

    suspend fun addClosure(roomId: Long, date: LocalDate, start: Int, end: Int, reason: String,
                           cancelConflicts: Boolean): Result<Closure> = apiCall(gson) {
        api.createClosure(
            roomId,
            ClosureRequest(BookingRules.toApi(date, start), BookingRules.toApi(date, end), reason.trim()),
            cancelConflicts,
        ).toDomain()
    }

    suspend fun deleteClosure(id: Long): Result<Unit> = apiCall(gson) { api.deleteClosure(id).orThrow() }

    // users
    suspend fun users(): Result<List<User>> = apiCall(gson) { api.users().map { it.toDomain() } }

    suspend fun setRole(userId: Long, role: Role): Result<User> =
        apiCall(gson) { api.setRole(userId, RoleRequest(role.name)).toDomain() }
}
