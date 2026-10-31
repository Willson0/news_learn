package app.karta.likvidnosti.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer

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

    /**
     * Фото как JPEG. Исходники с iPhone (HEIC) сервер и клиенты показывают пустыми,
     * поэтому картинку декодируем на устройстве и кодируем в JPEG (качество 85).
     */
    fun jpegPart(context: Context, field: String, uri: Uri): MultipartBody.Part? {
        val bytes = jpegBytes(context, uri) ?: return null
        val base = displayName(context, uri).substringBeforeLast('.', "photo")
        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, "$base.jpg", body)
    }

    /** Фото, перекодированное в JPEG (до 2048 px), или null, если прочитать не удалось. */
    fun jpegBytes(context: Context, uri: Uri): ByteArray? {
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull() ?: return null
        // Длинная сторона до 2048 px и JPEG 80: файл в разы меньше оригинала (3–4 МБ).
        val scaled = downscale(bitmap, 2048)
        val out = ByteArrayOutputStream()
        val encoded = scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        if (!encoded) return null
        return out.toByteArray()
    }

    /**
     * Тело запроса, которое сообщает, сколько байт уже ушло: кольцо загрузки на
     * превью фото в чате. Общий счётчик на все файлы сообщения.
     */
    class ProgressBody(
        private val delegate: RequestBody,
        private val counter: java.util.concurrent.atomic.AtomicLong,
        private val total: Long,
        private val onProgress: (Float) -> Unit,
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun writeTo(sink: okio.BufferedSink) {
            val counting = object : okio.ForwardingSink(sink) {
                override fun write(source: okio.Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    val sent = counter.addAndGet(byteCount)
                    if (total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            val buffered = counting.buffer()
            delegate.writeTo(buffered)
            buffered.flush()
        }
    }

    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).roundToInt().coerceAtLeast(1),
            (src.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }
}
