package com.example.roombooking.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.roombooking.domain.User
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "session")

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

/** Holds the JWT and the signed-in user. Survives app restarts via DataStore. */
class SessionManager(
    private val context: Context,
    private val gson: Gson,
    private val appScope: CoroutineScope,
) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER = stringPreferencesKey("user")
        val LAST_NOTIFICATION_ID = longPreferencesKey("last_notification_id")
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Read by the OkHttp interceptor on a background thread. */
    @Volatile
    var token: String? = null
        private set

    val currentUser: User?
        get() = (state.value as? SessionState.LoggedIn)?.user

    suspend fun restore() {
        val prefs = context.sessionStore.data.first()
        val savedToken = prefs[Keys.TOKEN]
        val savedUser = prefs[Keys.USER]?.let { gson.fromJson(it, User::class.java) }
        token = savedToken
        _state.value = if (savedToken != null && savedUser != null) {
            SessionState.LoggedIn(savedUser)
        } else {
            SessionState.LoggedOut
        }
    }

    suspend fun save(newToken: String, user: User) {
        context.sessionStore.edit {
            it[Keys.TOKEN] = newToken
            it[Keys.USER] = gson.toJson(user)
        }
        token = newToken
        _state.value = SessionState.LoggedIn(user)
    }

    suspend fun logout() {
        context.sessionStore.edit { it.clear() }
        token = null
        _state.value = SessionState.LoggedOut
    }

    fun onUnauthorized() {
        appScope.launch { logout() }
    }

    suspend fun lastNotificationId(): Long =
        context.sessionStore.data.map { it[Keys.LAST_NOTIFICATION_ID] ?: 0L }.first()

    suspend fun setLastNotificationId(id: Long) {
        context.sessionStore.edit { it[Keys.LAST_NOTIFICATION_ID] = id }
    }
}
