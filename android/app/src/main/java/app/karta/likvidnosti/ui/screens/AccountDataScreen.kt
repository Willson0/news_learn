package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenHeader
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

@Composable
fun AccountDataScreen(
    onBack: () -> Unit,
    onChange: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var email by remember { mutableStateOf(Session.user?.email ?: "") }
    var phone by remember { mutableStateOf(Session.user?.phone ?: "") }
    var revealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Repository.profile() }.getOrNull()?.user?.let {
            email = it.email ?: ""; phone = it.phone ?: ""
        }
    }

    Screen(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Данные пользователя", onBack = onBack)

        Column(
            modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            DataRow("Email:", email, AppIcons.Pencil) { onChange("email") }
            Divider()
            DataRow("Номер телефона:", phone, AppIcons.Pencil) { onChange("phone") }
            Divider()
            DataRow("Пароль:", if (revealed) "password" else "●●●●●●●", if (revealed) AppIcons.Eye else AppIcons.EyeClosed) { revealed = !revealed }
        }

        Text("Сменить пароль", color = T.TextMuted, fontSize = T.FsBase, modifier = Modifier.align(Alignment.End).clickable { onChange("password") })

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Сменить пароль", variant = ButtonVariant.Accent) { onChange("password") }
            AppButton("Выйти из аккаунта", variant = ButtonVariant.Danger) {
                scope.launch {
                    runCatching { Repository.logout() }
                    onLogout()
                }
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String, icon: Int, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = T.TextSecondary, fontSize = T.FsBase)
            Text(value, color = T.Text, fontSize = T.FsBase)
        }
        Box(modifier = Modifier.size(28.dp).clickable { onAction() }, contentAlignment = Alignment.Center) {
            AppIcon(icon, size = 22.dp)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
}
