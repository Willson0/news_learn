package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.UserProfileDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AvatarCircle
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.GlassBackButton
import app.karta.likvidnosti.ui.components.RemoteImage
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import kotlin.math.abs

private fun plural(n: Int, forms: Triple<String, String, String>): String {
    val a = abs(n) % 100
    val b = a % 10
    return when {
        a in 11..19 -> forms.third
        b in 2..4 -> forms.second
        b == 1 -> forms.first
        else -> forms.third
    }
}

/**
 * «Чат Профиль пользователя» (Figma 337:30766) — порт iOS `UserProfileView`:
 * фон #3B5839 с размытой аватаркой (blur 50 + чёрный 50 %), «назад» на y=78;
 * «Зарегистрирован» Kreadon 24 на y=274, дата 14 белый 65 % через 12, аватар
 * 148 через 40, имя Kreadon 20 через 12, тег 14 через 4.
 */
@Composable
fun UserProfileScreen(userId: Long, onBack: () -> Unit) {
    var user by remember { mutableStateOf<UserProfileDto?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        user = runCatching { Repository.userProfile(userId) }.getOrNull()
        loading = false
    }

    val u = user
    val registeredAt = u?.registeredAt
    val registeredDays = u?.registeredDays
    val registered = when {
        registeredAt == null -> ""
        registeredDays == null -> registeredAt
        else -> "$registeredAt ($registeredDays ${plural(registeredDays, Triple("день", "дня", "дней"))})"
    }

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        contentModifier = Modifier.background(hex("#3b5839")),
        content = {
            RemoteImage(
                fileUrl(u?.avatarUrl),
                Modifier.fillMaxSize().blur(50.dp, BlurredEdgeTreatment.Rectangle),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

            if (u != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = designTop(274.dp))
                        .padding(horizontal = T.ScreenX),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Зарегистрирован", style = AppFont.heading(T.FsTitle))
                    Text(
                        registered,
                        style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    AvatarCircle(
                        fileUrl(u.avatarUrl),
                        modifier = Modifier.padding(top = 40.dp),
                        size = 148.dp,
                        name = u.name ?: u.username,
                    )
                    Text(
                        u.name ?: "",
                        style = AppFont.heading(20.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    val un = u.username
                    if (!un.isNullOrEmpty()) {
                        Text(
                            if (un.startsWith("@")) un else "@$un",
                            style = AppFont.sans(T.FsBase),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else if (!loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Пользователь не найден", style = AppFont.heading(T.FsTitle))
                }
            }
        },
        overlay = {
            GlassBackButton(
                modifier = Modifier.padding(start = T.ScreenX, top = designTop(T.TopBarY)),
                onClick = onBack,
            )
        },
    )
}
