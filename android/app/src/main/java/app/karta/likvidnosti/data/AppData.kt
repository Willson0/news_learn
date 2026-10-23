package app.karta.likvidnosti.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.api.ReportDto
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

    /** Сбрасывает кэш (при выходе/смене аккаунта). */
    fun reset() {
        reports = null
        chats = null
    }

    /** Параллельно подгружает ленту (фильтр «все») и чаты. Ошибки гасит. */
    suspend fun preload() = coroutineScope {
        launch { runCatching { Repository.reports() }.getOrNull()?.let { reports = it } }
        launch {
            runCatching { Repository.chats() }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { chats = it }
        }
    }
}
