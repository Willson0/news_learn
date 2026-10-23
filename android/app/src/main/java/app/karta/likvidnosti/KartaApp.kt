package app.karta.likvidnosti

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.karta.likvidnosti.data.TokenStore
import app.karta.likvidnosti.push.KartaMessagingService
import app.karta.likvidnosti.push.Push

class KartaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
        Push.init(this)
        createNotificationChannel()
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
