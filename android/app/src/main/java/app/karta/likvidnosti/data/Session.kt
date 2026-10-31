package app.karta.likvidnosti.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.data.api.Network
import app.karta.likvidnosti.data.api.SubscriptionDto
import app.karta.likvidnosti.data.api.UserDto
import app.karta.likvidnosti.data.api.apiCall

/**
 * Текущий пользователь и его права (аналог frontend/src/api/session.js и
 * iOS `Data/Session.swift`).
 */
object Session {
    var user by mutableStateOf<UserDto?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Активна ли подписка (null — ещё не знаем). Правило — [SubscriptionDto.active]. */
    var subscriptionActive by mutableStateOf<Boolean?>(null)
        private set

    /** Дата окончания последнего известного статуса: без сети истёкшая подписка всё равно закрывается. */
    private var subscriptionUntil: String? = null

    val isAdmin: Boolean get() = user?.isAdmin == true

    /**
     * «Доступный экран профиля» из макета: без активной подписки (и не админ)
     * доступны только общий чат и профиль с оплатой.
     */
    val restricted: Boolean get() = user != null && !isAdmin && subscriptionActive == false

    /**
     * Холодный старт без сети: пользователь и подписка с прошлого раза — права
     * (админ, режим без подписки) известны с первого кадра, разлогина нет.
     * Возвращает true, если пользователь нашёлся в кэше.
     */
    fun seedFromDisk(): Boolean {
        if (!TokenStore.isAuthenticated) return false
        AppData.cachedUser()?.let { user = it }
        AppData.subscription?.let { setSubscription(it) }
        return user != null
    }

    /** После входа подписку узнаём до показа приложения: без неё сразу откроется профиль. */
    suspend fun setCurrent(u: UserDto?) {
        subscriptionActive = null
        subscriptionUntil = null
        user = u
        loaded = true
        loadSubscription()
    }

    fun clear() {
        user = null
        subscriptionActive = null
        subscriptionUntil = null
        AppData.reset()
    }

    /** Подтягивает статус подписки; при ошибке оставляет прежнее значение, но истёкший срок закрывает доступ. */
    suspend fun loadSubscription() {
        if (!TokenStore.isAuthenticated) {
            subscriptionActive = null
            subscriptionUntil = null
            return
        }
        // Запуск прошёл без сети — пользователя дочитываем при первой возможности.
        if (user == null) runCatching { apiCall { Network.api.me() }.user }.getOrNull()?.let { user = it }
        // Нет записи подписки (сервер отдаёт data: null) — это неактивная подписка.
        runCatching { Repository.subscription() }
            .onSuccess { setSubscription(it) }
            .onFailure {
                if (subscriptionActive == true && !SubscriptionDto.untilNotPassed(subscriptionUntil)) {
                    subscriptionActive = false
                }
            }
    }

    /**
     * Свежий статус подписки (из профиля, экрана подписки или обновления):
     * после оплаты приложение разблокируется на месте — навбар и экраны
     * переходят в полный режим.
     */
    fun setSubscription(sub: SubscriptionDto?) {
        subscriptionUntil = sub?.until
        subscriptionActive = sub?.active ?: false
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
