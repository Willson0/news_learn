package app.karta.likvidnosti.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Сборка multipart-частей для загрузки отчётов и фото чатов.
 */
object Uploads {
    fun textPart(name: String, value: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(name, value)

    /** Имя файла по content-Uri (для отображения и отправки). */
    fun displayName(context: Context, uri: Uri): String {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.let { name = it }
        }
        return name
    }

    /** Часть с файлом из content-Uri. Возвращает null, если прочитать не удалось. */
    fun filePart(context: Context, field: String, uri: Uri): MultipartBody.Part? {
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return null
        val type = resolver.getType(uri) ?: "application/octet-stream"
        val body = bytes.toRequestBody(type.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, displayName(context, uri), body)
    }
}
