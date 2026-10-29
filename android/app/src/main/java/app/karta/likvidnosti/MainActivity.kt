package app.karta.likvidnosti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.karta.likvidnosti.data.TokenStore
import app.karta.likvidnosti.ui.AppBoot
import app.karta.likvidnosti.ui.AppRoot
import app.karta.likvidnosti.ui.theme.KartaTheme

class MainActivity : ComponentActivity() {

    /** Разрешение на уведомления (Android 13+) просим после входа, не при первом запуске. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Логотип держится, пока AppRoot не загрузил сессию.
        installSplashScreen().setKeepOnScreenCondition { !AppBoot.ready }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleYandexLink(intent)
        handlePushLink(intent)
        askNotificationPermissionIfNeeded()
        setContent {
            KartaTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleYandexLink(intent)
        handlePushLink(intent)
    }

    private fun handleYandexLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "karta" && uri.host == "auth") YandexSignIn.callbacks.tryEmit(uri)
        if (uri.scheme == "karta" && uri.host == "billing") BillingReturn.results.tryEmit(uri)
    }

    /** Нажатие на push со ссылкой (например, счёт на оплату) открывает ссылку в браузере внутри приложения. */
    private fun handlePushLink(intent: Intent?) {
        val url = intent?.getStringExtra("url") ?: return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || !TokenStore.isAuthenticated) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
