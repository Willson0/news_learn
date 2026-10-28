package app.karta.likvidnosti

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.karta.likvidnosti.data.TokenStore
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.ImageResult
import app.karta.likvidnosti.push.KartaMessagingService
import app.karta.likvidnosti.push.Push

class KartaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
        Push.init(this)
        createNotificationChannel()
    }

    /**
     * Общий загрузчик картинок: кэш в памяти и на диске (250 МБ, переживает
     * перезапуск). Кэш-заголовки сервера не учитываются, а ключ — адрес без query:
     * у обложек и вложений подписанные ссылки, `signature`/`expires` меняются при
     * каждом запросе, а файл тот же — без этого картинка каждый раз качалась заново.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .components { add(StableKeyInterceptor) }
        .crossfade(false)
        .build()

    private object StableKeyInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val raw = when (val data = request.data) {
                is String -> data
                is android.net.Uri -> data.toString()
                else -> null
            }
            if (raw == null || !(raw.startsWith("http://") || raw.startsWith("https://")) ||
                request.diskCacheKey != null
            ) {
                return chain.proceed(request)
            }
            val key = raw.substringBefore('#').substringBefore('?')
            return chain.proceed(request.newBuilder().memoryCacheKey(key).diskCacheKey(key).build())
        }
    }

    /** Канал для push-уведомлений (обязателен с Android 8). */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            KartaMessagingService.CHANNEL_ID,
            "Уведомления",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
