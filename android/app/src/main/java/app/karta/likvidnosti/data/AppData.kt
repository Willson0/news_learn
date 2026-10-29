package app.karta.likvidnosti.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.data.api.BillingCardDto
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.api.ProfileResponse
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.api.SubscriptionDto
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

    /**
     * Профиль, подписка и карта: экраны «Профиль», «Управление подпиской» и
     * «Уведомления» сразу открываются в состоянии пользователя, без анимации
     * переключателей и статусов из «нулевого» состояния.
     */
    var profile by mutableStateOf<ProfileResponse?>(null)
    var subscription by mutableStateOf<SubscriptionDto?>(null)
    var card by mutableStateOf<BillingCardDto?>(null)

    /** Сбрасывает кэш (при выходе/смене аккаунта). */
    fun reset() {
        reports = null
        reportsAt = null
        chats = null
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
                ?.takeIf { it.isNotEmpty() }?.let { chats = it }
        }
    }
}
