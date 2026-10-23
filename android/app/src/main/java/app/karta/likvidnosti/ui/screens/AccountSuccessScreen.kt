package app.karta.likvidnosti.ui.screens

import androidx.compose.runtime.Composable
import app.karta.likvidnosti.ui.components.AuthMessageView

/** «успешно» (iOS AccountSuccessView, Figma 363:9535 …): Kreadon 24 по центру экрана. */
@Composable
fun AccountSuccessScreen(field: String, onBack: () -> Unit) {
    val message = when (field) {
        "email" -> "Вы успешно сменили почту"
        "phone" -> "Вы успешно сменили номер"
        "password" -> "Вы успешно сменили пароль"
        else -> "Готово"
    }
    AuthMessageView(message, showBack = true, onBack = onBack)
}
