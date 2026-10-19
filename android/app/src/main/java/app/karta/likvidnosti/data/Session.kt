package app.karta.likvidnosti.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.data.api.Network
import app.karta.likvidnosti.data.api.UserDto
import app.karta.likvidnosti.data.api.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Текущий пользователь и его права (аналог frontend/src/api/session.js и
 * iOS `Data/Session.swift`).
 */
object Session {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var user by mutableStateOf<UserDto?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Активна ли подписка (null — ещё не знаем). */
    var subscriptionActive by mutableStateOf<Boolean?>(null)
        private set

    val isAdmin: Boolean get() = user?.isAdmin == true

    /**
     * «Доступный экран профиля» из макета: без активной подписки (и не админ)
     * доступны только общий чат и профиль с оплатой.
     */
    val restricted: Boolean get() = user != null && !isAdmin && subscriptionActive == false

    fun setCurrent(u: UserDto?) {
        user = u
        loaded = true
        scope.launch { loadSubscription() }
    }

    fun clear() {
        user = null
        subscriptionActive = null
        AppData.reset()
    }

    /** Подтягивает статус подписки; при ошибке оставляет прежнее значение. */
    suspend fun loadSubscription() {
        if (!TokenStore.isAuthenticated) {
            subscriptionActive = null
            return
        }
        runCatching { Repository.subscription() }.getOrNull()?.let { subscriptionActive = it.active }
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
        }
        loadSubscription()
        loaded = true
    }
}
