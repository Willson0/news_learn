package app.karta.likvidnosti.data

import android.content.Context
import app.karta.likvidnosti.data.api.Network
import kotlinx.serialization.DeserializationStrategy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.Executors

/**
 * Последние ответы основных запросов (лента, чаты, профиль, подписка, пользователь)
 * на диске (iOS OfflineCache). При повторном запуске — и без сети — экраны сразу
 * показывают их, а свежие данные приходят тихо в фоне. Стирается при выходе
 * из аккаунта и истёкшей сессии.
 */
object OfflineCache {
    private val cachedPaths = setOf("reports", "chats", "profile", "subscription", "user")
    private val io = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "offline").apply { mkdirs() }
    }

    private fun file(path: String): File? = dir?.let { File(it, path.replace('/', '_') + ".json") }

    /** Путь запроса относительно API (без query), если его ответ нужно сохранять. */
    fun pathFor(url: HttpUrl, base: String): String? {
        if (url.query != null) return null
        val basePath = base.toHttpUrlOrNull()?.encodedPath?.trimEnd('/') ?: ""
        val path = url.encodedPath.removePrefix(basePath).trim('/')
        return path.takeIf { it in cachedPaths }
    }

    fun save(path: String, bytes: ByteArray) {
        val f = file(path) ?: return
        io.execute {
            runCatching {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(f)
            }
        }
    }

    /** Сохранённый ответ и время его прихода (дата изменения файла). */
    fun <T> load(path: String, strategy: DeserializationStrategy<T>): Pair<T, Long>? {
        val f = file(path)?.takeIf { it.exists() } ?: return null
        return runCatching { Network.json.decodeFromString(strategy, f.readText()) to f.lastModified() }.getOrNull()
    }

    /** Выход из аккаунта или истёкшая сессия: стираем кэш и очередь неотправленного. */
    fun clear() {
        ChatOutbox.clearAll()
        val d = dir ?: return
        io.execute { d.listFiles()?.forEach { it.delete() } }
    }
}
