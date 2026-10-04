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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T

@Composable
fun UserProfileScreen(onBack: () -> Unit) {
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Зарегистрирован", color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
            Text("14.09.2026 (7 дней)", color = T.TextSecondary, fontSize = T.FsBase, modifier = Modifier.padding(bottom = 20.dp))
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF6B7350), Color(0xFF3F4A2C))))
                    .padding(bottom = 14.dp),
            )
            Text("Артем", color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
            Text("@AlexeyRub", color = T.TextSecondary, fontSize = T.FsMd)
        }
    }
}
