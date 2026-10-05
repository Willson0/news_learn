package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T

@Composable
fun AccountSuccessScreen(field: String, onBack: () -> Unit) {
    val message = when (field) {
        "phone" -> "Вы успешно сменили номер"
        "password" -> "Вы успешно сменили пароль"
        "email" -> "Вы успешно сменили почту"
        else -> "Готово"
    }
    Box(modifier = Modifier.fillMaxSize().background(T.Bg), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = T.ScreenX, top = insetTop() + 16.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(T.SurfaceMuted)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, size = 22.dp) }

        Text(
            message, color = T.Text, fontSize = T.FsLg, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = T.ScreenX),
        )
    }
}
