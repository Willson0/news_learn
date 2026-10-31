package app.karta.likvidnosti.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.data.api.BillingCardDto
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.api.ProfileResponse
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.api.SubscriptionDto
import app.karta.likvidnosti.data.api.MeResponse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import app.karta.likvidnosti.data.api.DataWrapper

/**
 * Предзагруженные «основные» данные приложения: лента материалов и список
 * чатов. Загружаются один раз при старте (после авторизации), чтобы при
 * базовой навигации между вкладками не было прогрузок/скачков вёрстки.
 *
 * `null` — ещё не загружено (экран показывает скелетоны); непустой/пустой
 * список — загружено.
 */
object AppData {
    var reports by mutableStateOf<List<ReportDto>?>(null)
    var chats by mutableStateOf<List<ChatSectionDto>?>(null)

    /** Когда лента последний раз пришла с сервера — для плашки «Показаны данные от 14:05». */
    var reportsAt by mutableStateOf<Long?>(null)
    /** Когда список чатов последний раз пришёл с сервера. */
    var chatsAt by mutableStateOf<Long?>(null)

    /**
     * Профиль, подписка и карта: экраны «Профиль», «Управление подпиской» и
     * «Уведомления» сразу открываются в состоянии пользователя, без анимации
     * переключателей и статусов из «нулевого» состояния.
     */
    var profile by mutableStateOf<ProfileResponse?>(null)
    var subscription by mutableStateOf<SubscriptionDto?>(null)
    var card by mutableStateOf<BillingCardDto?>(null)

    /**
     * Холодный старт: последние данные с диска (лента, чаты, профиль, подписка) —
     * экраны открываются сразу с ними, даже без сети, свежие приходят в фоне.
     */
    fun seedFromDisk() {
        if (!TokenStore.isAuthenticated) return
        OfflineCache.load("reports", DataWrapper.serializer(ListSerializer(ReportDto.serializer())))?.let { (w, at) ->
            reports = w.data ?: emptyList(); reportsAt = at
        }
        OfflineCache.load("chats", DataWrapper.serializer(ListSerializer(ChatSectionDto.serializer())))?.let { (w, at) ->
            w.data?.takeIf { it.isNotEmpty() }?.let { chats = it; chatsAt = at }
        }
        OfflineCache.load("profile", ProfileResponse.serializer())?.let { (p, _) ->
            profile = p
            p.subscription?.let { subscription = it }
        }
        OfflineCache.load("subscription", DataWrapper.serializer(SubscriptionDto.serializer()))?.first?.data?.let { subscription = it }
    }

    /** Пользователь с прошлого запуска (из ответа /user или профиля). */
    fun cachedUser() =
        OfflineCache.load("user", MeResponse.serializer())?.first?.user ?: profile?.user

    /** Сбрасывает кэш (при выходе/смене аккаунта). */
    fun reset() {
        OfflineCache.clear()
        reports = null
        reportsAt = null
        chats = null
        chatsAt = null
        profile = null
        subscription = null
        card = null
    }

    /** Перечитывает профиль (после изменения настроек), ошибки гасит. */
    suspend fun refreshProfile() {
        runCatching { Repository.profile() }.getOrNull()?.let { p ->
            profile = p
            p.subscription?.let { subscription = it }
        }
    }

    /** Параллельно подгружает ленту (фильтр «все») и чаты. Ошибки гасит. */
    suspend fun preload() = coroutineScope {
        launch { runCatching { Repository.reports() }.getOrNull()?.let { reports = it; reportsAt = System.currentTimeMillis() } }
        launch { refreshProfile() }
        launch {
            runCatching { Repository.chats() }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { chats = it; chatsAt = System.currentTimeMillis() }
        }
    }
}
