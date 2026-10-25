package app.karta.likvidnosti

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.MutableSharedFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Вход через Яндекс: приложение открывает /auth/yandex/start?challenge=…, сервер возвращает
 * karta://auth?code=… или ?error=…. Одноразовый code меняется на токен вместе с verifier,
 * который знает только это приложение, поэтому чужая ссылка karta://auth ни во что не войдёт.
 */
object YandexSignIn {
    /** Ссылка из браузера, которую MainActivity передаёт экрану входа. */
    val callbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /** verifier начатого входа и время начала; без него ссылка karta://auth игнорируется. */
    @Volatile private var pending: Pair<String, Long>? = null
    private const val TTL_MS = 10 * 60 * 1000L

    fun start(context: Context) {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = base64url(bytes)
        pending = verifier to System.currentTimeMillis()
        val challenge = base64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = BuildConfig.API_BASE_URL.removeSuffix("/") + "/auth/yandex/start?challenge=" + challenge
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Забирает verifier начатого входа (один раз). null — вход не начинали или он устарел. */
    fun takeVerifier(): String? {
        val p = pending ?: return null
        pending = null
        return if (System.currentTimeMillis() - p.second <= TTL_MS) p.first else null
    }

    private fun base64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
