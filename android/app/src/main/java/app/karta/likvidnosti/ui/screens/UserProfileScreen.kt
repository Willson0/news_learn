package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.UserProfileDto
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage
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

@Composable
fun UserProfileScreen(userId: Long, onBack: () -> Unit) {
    var user by remember { mutableStateOf<UserProfileDto?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        user = runCatching { Repository.userProfile(userId) }.getOrNull()
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF4A5A54), Color(0xFF2A2D2E), Color(0xFF1A1B1D))),
            ),
        contentAlignment = Alignment.Center,
    ) {
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

        val u = user
        if (u != null) {
            val registered = buildString {
                append(u.registeredAt ?: "")
                if (u.registeredDays != null) {
                    val word = plural(u.registeredDays, Triple("день", "дня", "дней"))
                    append(" (${u.registeredDays} $word)")
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Зарегистрирован", color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
                Text(registered, color = T.TextSecondary, fontSize = T.FsBase, modifier = Modifier.padding(bottom = 20.dp))
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF6B7350), Color(0xFF3F4A2C))))
                        .padding(bottom = 14.dp),
                ) {
                    val av = u.avatarUrl
                    if (!av.isNullOrEmpty()) {
                        AsyncImage(model = av, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    }
                }
                Text(u.name ?: "", color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                if (!u.username.isNullOrEmpty()) Text(u.username!!, color = T.TextSecondary, fontSize = T.FsMd)
                if (!u.seen.isNullOrEmpty()) Text(u.seen!!, color = T.TextMuted, fontSize = T.FsSm)
            }
        } else if (!loading) {
            Text("Пользователь не найден", color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
        }
    }
}
