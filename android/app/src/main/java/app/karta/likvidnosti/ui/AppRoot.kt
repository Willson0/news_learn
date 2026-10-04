package app.karta.likvidnosti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.TokenStore
import app.karta.likvidnosti.ui.screens.LoginScreen
import app.karta.likvidnosti.ui.theme.T

/**
 * Корень приложения. Временная версия: грузит сессию и показывает вход.
 * Полноценный NavHost подключается после готовности экранов.
 */
@Composable
fun AppRoot() {
    var ready by remember { mutableStateOf(false) }
    var authed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (TokenStore.isAuthenticated) {
            runCatching { Session.load() }
            authed = Session.user != null
        }
        ready = true
    }

    Box(modifier = Modifier.fillMaxSize().background(T.Bg), contentAlignment = Alignment.Center) {
        if (!ready) {
            Text("…", color = T.TextMuted)
        } else if (!authed) {
            LoginScreen(
                onAuthed = { authed = true },
                onRecovery = {},
                onNeedConfirm = {},
            )
        } else {
            Text("Главная", color = T.Text)
        }
    }
}
