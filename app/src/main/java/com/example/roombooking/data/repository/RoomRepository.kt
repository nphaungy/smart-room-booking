package com.example.roombooking.data.repository

import com.example.roombooking.data.local.RoomDao
import com.example.roombooking.data.local.RoomEntity
import com.example.roombooking.data.remote.ApiService
import com.example.roombooking.data.remote.apiCall
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.Equipment
import com.example.roombooking.domain.Room
import com.example.roombooking.domain.RoomSchedule
import com.example.roombooking.domain.RoomSearch
import com.example.roombooking.domain.RoomType
import com.example.roombooking.domain.toDomain
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomRepository(
    private val api: ApiService,
    private val dao: RoomDao,
    private val gson: Gson,
) {
    private val equipmentListType = object : TypeToken<List<Equipment>>() {}.type

    /** RB02: cached list, shown immediately (also offline). */
    val cachedRooms: Flow<List<Room>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun refresh(): Result<Unit> = apiCall(gson) {
        val rooms = api.rooms().map { it.toDomain() }
        dao.replaceAll(rooms.map { it.toEntity() })
    }

    /** RB03: always online, because availability must come from the server. */
    suspend fun search(filter: RoomSearch): Result<List<Room>> = apiCall(gson) {
        val start = if (filter.hasTimeRange) BookingRules.toApi(filter.date!!, filter.startMinute!!) else null
        val end = if (filter.hasTimeRange) BookingRules.toApi(filter.date!!, filter.endMinute!!) else null
        api.rooms(
            start = start, end = end, attendees = filter.attendees, typeId = filter.typeId,
            q = filter.query?.trim()?.ifBlank { null },
        ).map { it.toDomain() }
    }

    suspend fun room(id: Long): Result<Room> {
        val remote = apiCall(gson) { api.room(id).toDomain() }
        if (remote.isSuccess) return remote
        // Offline fallback: use the cached copy if we have one
        return dao.find(id)?.let { Result.success(it.toDomain()) } ?: remote
    }

    suspend fun schedule(roomId: Long, date: LocalDate): Result<RoomSchedule> =
        apiCall(gson) { api.schedule(roomId, date.toString()).toDomain() }

    suspend fun roomTypes(): Result<List<RoomType>> =
        apiCall(gson) { api.roomTypes().map { RoomType(it.id, it.name) } }

    suspend fun equipment(): Result<List<Equipment>> =
        apiCall(gson) { api.equipment().map { Equipment(it.id, it.name) } }

    private fun Room.toEntity() = RoomEntity(
        id, code, name, building, floor, capacity, typeId, typeName, active, description, gson.toJson(equipment),
    )

    private fun RoomEntity.toDomain() = Room(
        id, code, name, building, floor, capacity, typeId, typeName, active, description,
        gson.fromJson<List<Equipment>>(equipmentJson, equipmentListType) ?: emptyList(),
    )
}
