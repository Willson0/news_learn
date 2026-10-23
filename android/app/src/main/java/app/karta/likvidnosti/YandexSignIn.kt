package app.karta.likvidnosti

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

/** Вход через Яндекс: сервер возвращает в приложение karta://auth?token=… или ?error=…. */
object YandexSignIn {
    /** Ссылка из браузера, которую MainActivity передаёт экрану входа. */
    val callbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    fun start(context: Context) {
        val url = BuildConfig.API_BASE_URL.removeSuffix("/") + "/auth/yandex/start"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
