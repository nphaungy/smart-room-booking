package com.example.roombooking.data.repository

import com.example.roombooking.data.SessionManager
import com.example.roombooking.data.local.NotificationDao
import com.example.roombooking.data.local.NotificationEntity
import com.example.roombooking.data.remote.ApiService
import com.example.roombooking.data.remote.NotificationDto
import com.example.roombooking.data.remote.apiCall
import com.example.roombooking.data.remote.orThrow
import com.example.roombooking.domain.AppNotification
import com.example.roombooking.domain.parseTime
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class NotificationRepository(
    private val api: ApiService,
    private val dao: NotificationDao,
    private val session: SessionManager,
    private val gson: Gson,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: Flow<List<AppNotification>> = session.state.flatMapLatest {
        val user = session.currentUser ?: return@flatMapLatest flowOf(emptyList<AppNotification>())
        dao.observe(user.id).map { rows -> rows.map { it.toDomain() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadCount: Flow<Int> = session.state.flatMapLatest {
        val user = session.currentUser ?: return@flatMapLatest flowOf(0)
        dao.observeUnread(user.id)
    }

    /** Full refresh for the notifications screen. */
    suspend fun refresh(): Result<Unit> = apiCall(gson) {
        val user = session.currentUser ?: return@apiCall
        dao.insertAll(api.notifications(0).map { it.toEntity(user.id) })
    }

    /**
     * Used by the background poller: returns only notifications newer than the last one
     * already shown as a system notification, then remembers the newest id.
     */
    suspend fun fetchNew(): Result<List<AppNotification>> = apiCall(gson) {
        val user = session.currentUser ?: return@apiCall emptyList()
        val lastId = session.lastNotificationId()
        val fresh = api.notifications(lastId).sortedBy { it.id }
        if (fresh.isNotEmpty()) {
            dao.insertAll(fresh.map { it.toEntity(user.id) })
            session.setLastNotificationId(fresh.last().id)
        }
        fresh.map { it.toEntity(user.id).toDomain() }
    }

    /** After login: treat everything that already exists as "seen" for system notifications. */
    suspend fun markBaseline() {
        apiCall(gson) {
            val user = session.currentUser ?: return@apiCall
            val all = api.notifications(0)
            dao.insertAll(all.map { it.toEntity(user.id) })
            session.setLastNotificationId(all.maxOfOrNull { it.id } ?: 0L)
        }
    }

    suspend fun markRead(id: Long): Result<Unit> = apiCall(gson) {
        api.markRead(id).orThrow()
        dao.markRead(id)
    }

    suspend fun markAllRead(): Result<Unit> = apiCall(gson) {
        val user = session.currentUser ?: return@apiCall
        api.markAllRead().orThrow()
        dao.markAllRead(user.id)
    }

    private fun NotificationDto.toEntity(userId: Long) =
        NotificationEntity(id, userId, bookingId, type, title, message, isRead, createdAt)

    private fun NotificationEntity.toDomain() =
        AppNotification(id, bookingId, type, title, message, isRead, parseTime(createdAt))
}
