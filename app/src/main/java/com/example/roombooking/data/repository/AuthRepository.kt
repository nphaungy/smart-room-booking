package com.example.roombooking.data.repository

import com.example.roombooking.data.SessionManager
import com.example.roombooking.data.local.AppDatabase
import com.example.roombooking.data.remote.ApiService
import com.example.roombooking.data.remote.LoginRequest
import com.example.roombooking.data.remote.RegisterRequest
import com.example.roombooking.data.remote.TokenDto
import com.example.roombooking.data.remote.apiCall
import com.example.roombooking.domain.User
import com.example.roombooking.domain.toDomain
import com.google.gson.Gson

class AuthRepository(
    private val api: ApiService,
    private val session: SessionManager,
    private val db: AppDatabase,
    private val notifications: NotificationRepository,
    private val gson: Gson,
) {
    suspend fun login(email: String, password: String): Result<User> =
        apiCall(gson) { api.login(LoginRequest(email.trim(), password)) }.mapCatching { onSignedIn(it) }

    suspend fun register(fullName: String, email: String, faculty: String?, password: String): Result<User> =
        apiCall(gson) {
            api.register(RegisterRequest(email.trim(), password, fullName.trim(), faculty?.trim()?.ifBlank { null }))
        }.mapCatching { onSignedIn(it) }

    private suspend fun onSignedIn(token: TokenDto): User {
        val user = token.user.toDomain()
        session.save(token.accessToken, user)
        // Don't flood the phone with every old notification after a fresh login.
        notifications.markBaseline()
        return user
    }

    suspend fun logout() {
        session.currentUser?.let {
            db.bookingDao().clearFor(it.id)
            db.notificationDao().clearFor(it.id)
        }
        session.logout()
    }
}
