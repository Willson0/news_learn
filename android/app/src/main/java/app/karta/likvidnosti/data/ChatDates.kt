package app.karta.likvidnosti.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Даты чата. Сервер отдаёт полное время в ISO 8601 (`created_at`, `last_at`),
 * приложение само переводит его в часовой пояс телефона и подписывает:
 * «Сегодня», «Вчера», «12 октября».
 */
object ChatDates {
    private val ru = Locale("ru")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ru)
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val dayYearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val shortFmt = DateTimeFormatter.ofPattern("dd.MM.yy", ru)

    fun parse(iso: String?): ZonedDateTime? {
        if (iso.isNullOrEmpty()) return null
        return runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
    }

    /** День сообщения; у ещё не отправленного (без даты) — сегодня. */
    fun day(iso: String?): LocalDate = parse(iso)?.toLocalDate() ?: LocalDate.now()

    /** «16:32» в часовом поясе телефона; без даты — то, что прислал сервер. */
    fun time(iso: String?, fallback: String? = null): String = parse(iso)?.format(timeFmt) ?: fallback ?: ""

    /** Разделитель в ленте: «Сегодня», «Вчера», «12 октября», «12 октября 2025». */
    fun dayLabel(day: LocalDate): String {
        val today = LocalDate.now()
        return when {
            day == today -> "Сегодня"
            day == today.minusDays(1) -> "Вчера"
            day.year == today.year -> day.format(dayFmt)
            else -> day.format(dayYearFmt)
        }
    }

    /** Список чатов: время для сегодня, «вчера», дальше дата. */
    fun listStamp(iso: String?, fallback: String? = null): String {
        val d = parse(iso) ?: return fallback ?: ""
        val day = d.toLocalDate()
        val today = LocalDate.now()
        return when {
            day == today -> d.format(timeFmt)
            day == today.minusDays(1) -> "вчера"
            day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMM", ru)).trimEnd('.')
            else -> day.format(shortFmt)
        }
    }

    /** Подпись в просмотре фото: «Сегодня в 16:32», «12 октября в 09:05». */
    fun stamp(iso: String?, fallback: String? = null): String {
        val d = parse(iso) ?: return fallback?.takeIf { it.isNotEmpty() } ?: ""
        return "${dayLabel(d.toLocalDate())} в ${d.format(timeFmt)}"
    }
}
