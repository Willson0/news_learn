package app.karta.likvidnosti.data

import android.content.Context
import app.karta.likvidnosti.data.api.Network
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Неотправленные сообщения чата (текст и фото) на диске (iOS ChatOutbox):
 * сообщение со статусом «не отправлено» не теряется при выходе из чата и
 * перезапуске, а повтор уходит с тем же ключом `clientId` — сервер не создаст дубль.
 */
object ChatOutbox {
    @Serializable
    data class Entry(
        val localId: Long,
        val clientId: String,
        val text: String? = null,
        val replyTo: Long? = null,
        val replyAuthor: String? = null,
        val replyText: String? = null,
        val isPrivate: Boolean = false,
        /** Пути jpeg-файлов фото в папке очереди. */
        val photos: List<String> = emptyList(),
    )

    private var dir: File? = null
    private val serializer = ListSerializer(Entry.serializer())

    fun init(context: Context) {
        dir = File(context.filesDir, "chat_outbox").apply { mkdirs() }
    }

    /** Папка для jpeg-файлов фото, которые ещё не ушли. */
    fun photoDir(): File? = dir

    private fun listFile(chat: String): File? =
        dir?.let { File(it, "chat-" + chat.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json") }

    @Synchronized
    fun entries(chat: String): List<Entry> {
        val f = listFile(chat)?.takeIf { it.exists() } ?: return emptyList()
        return runCatching { Network.json.decodeFromString(serializer, f.readText()) }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun write(chat: String, list: List<Entry>) {
        val f = listFile(chat) ?: return
        if (list.isEmpty()) { f.delete(); return }
        runCatching { f.writeText(Network.json.encodeToString(serializer, list)) }
    }

    @Synchronized
    fun add(chat: String, entry: Entry) {
        write(chat, entries(chat).filter { it.localId != entry.localId } + entry)
    }

    @Synchronized
    fun remove(chat: String, localId: Long) {
        val list = entries(chat)
        val entry = list.firstOrNull { it.localId == localId } ?: return
        entry.photos.forEach { File(it).delete() }
        write(chat, list.filter { it.localId != localId })
    }

    /** При выходе из аккаунта очередь стирается целиком. */
    @Synchronized
    fun clearAll() {
        dir?.listFiles()?.forEach { it.delete() }
    }
}
