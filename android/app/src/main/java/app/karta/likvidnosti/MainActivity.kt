package app.karta.likvidnosti

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.karta.likvidnosti.ui.AppRoot
import app.karta.likvidnosti.ui.theme.KartaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleYandexLink(intent)
        setContent {
            KartaTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleYandexLink(intent)
    }

    private fun handleYandexLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "karta" && uri.host == "auth") YandexSignIn.callbacks.tryEmit(uri)
    }
}
