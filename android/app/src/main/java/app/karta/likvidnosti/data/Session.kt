package app.karta.likvidnosti.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.data.api.Network
import app.karta.likvidnosti.data.api.UserDto
import app.karta.likvidnosti.data.api.apiCall

/**
 * Текущий пользователь и его права (аналог frontend/src/api/session.js).
 */
object Session {
    var user by mutableStateOf<UserDto?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    val isAdmin: Boolean get() = user?.isAdmin == true

    fun setCurrent(u: UserDto?) {
        user = u
        loaded = true
    }

    fun clear() {
        user = null
    }

    /** Подгружает пользователя, если есть токен. Тихо гасит ошибки. */
    suspend fun load(force: Boolean = false) {
        if (!TokenStore.isAuthenticated) {
            user = null
            loaded = true
            return
        }
        if (loaded && !force && user != null) return
        try {
            user = apiCall { Network.api.me() }.user
        } catch (_: Exception) {
            // оставляем прежнее значение / null
        } finally {
            loaded = true
        }
    }
}
