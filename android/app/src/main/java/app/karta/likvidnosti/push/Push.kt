package app.karta.likvidnosti.push

import android.content.Context
import app.karta.likvidnosti.data.TokenStore
import app.karta.likvidnosti.data.api.DeviceBody
import app.karta.likvidnosti.data.api.Network
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Регистрация FCM-токена устройства на бэкенде (POST /api/devices) и её отмена при выходе.
 * Если Firebase не настроен (нет google-services.json), все методы тихо ничего не делают.
 */
object Push {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun firebaseReady(): Boolean {
        val ctx = appContext ?: return false
        return FirebaseApp.getApps(ctx).isNotEmpty()
    }

    /** Текущий FCM-токен или null, если Firebase не настроен или токен не получен. */
    private suspend fun currentToken(): String? {
        if (!firebaseReady()) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
        }
    }

    /** Отправляет токен на бэкенд. Вызывается после входа и при обновлении токена FCM. */
    fun syncToken() {
        scope.launch {
            val token = currentToken() ?: return@launch
            registerToken(token)
        }
    }

    /** Токен, пришедший от FCM (onNewToken): регистрируем, если пользователь вошёл. */
    fun onNewToken(token: String) {
        if (!TokenStore.isAuthenticated) return
        scope.launch { registerToken(token) }
    }

    /** Отвязываем устройство перед выходом, пока токен входа ещё действует. */
    suspend fun unregisterCurrent() {
        val token = currentToken() ?: return
        runCatching { Network.api.unregisterDevice(DeviceBody(token)) }
    }

    private suspend fun registerToken(token: String) {
        if (!TokenStore.isAuthenticated) return
        runCatching { Network.api.registerDevice(DeviceBody(token)) }
    }
}
