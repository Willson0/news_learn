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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
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

    /** Просьба к MainActivity показать запрос разрешения на уведомления (Android 13+) — сразу после входа. */
    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val permissionRequests: SharedFlow<Unit> = _permissionRequests

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

    /**
     * Отправляет токен на бэкенд. Вызывается после входа и при каждом запуске приложения:
     * раньше токен регистрировался только при входе, и если запрос тогда не прошёл (нет сети,
     * сервер недоступен) или вход был в сборке без Firebase, устройство так и оставалось без push.
     */
    fun syncToken() {
        _permissionRequests.tryEmit(Unit)
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
        // Несколько попыток с паузой: запуск часто совпадает с плохой сетью.
        repeat(3) { attempt ->
            if (!TokenStore.isAuthenticated) return
            if (runCatching { Network.api.registerDevice(DeviceBody(token)) }.isSuccess) return
            delay(5_000L * (attempt + 1))
        }
    }
}
