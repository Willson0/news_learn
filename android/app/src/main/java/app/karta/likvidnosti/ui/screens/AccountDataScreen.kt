package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.IconDef
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.hex
import kotlinx.coroutines.launch

/**
 * «Профиль Данные аккаунта» (iOS AccountDataView, Figma 337:27019 / 337:27032):
 * заголовок на y=160, блок с отступами 16/12 и строками 48 (подпись 14 белый
 * 65 %, значение белым, справа иконка 24: карандаш или глаз), пароль — семь
 * точек 12 (#D9D9D9) через 2; под блоком справа «Сменить пароль» 12; через
 * 24 — «Сменить пароль» и красная «Выйти из аккаунта».
 */
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
            email = it.email ?: ""
            phone = it.phone ?: ""
        }
    }

    InnerScreen(onBack = onBack) {
        Text("Данные пользователя", style = AppFont.heading(T.FsTitle))

        Column(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .clip(T.ShapeCard)
                .background(T.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            DataRow(AppIcons.pencil, { onChange("email") }) { ValueText("Email:", email) }
            DataRow(AppIcons.pencil, { onChange("phone") }) { ValueText("Номер телефона:", phone) }
            DataRow(if (revealed) AppIcons.eye else AppIcons.eyeClosed, { revealed = !revealed }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Пароль:", style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                    if (revealed) {
                        Text("password", style = AppFont.sans(T.FsBase))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(7) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(hex("#d9d9d9")))
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Сменить пароль",
            style = AppFont.sans(T.FsSm, color = T.TextSecondary),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 12.dp)
                .pressable { onChange("password") },
        )

        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Сменить пароль", variant = ButtonVariant.Accent) { onChange("password") }
            var loggingOut by remember { mutableStateOf(false) }
            AppButton("Выйти из аккаунта", variant = ButtonVariant.Danger, loading = loggingOut) {
                loggingOut = true
                scope.launch {
                    runCatching { Repository.logout() }
                    onLogout()
                }
            }
        }
    }
}

@Composable
private fun ValueText(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = T.TextSecondary)) { append("$label ") }
            withStyle(SpanStyle(color = T.Text)) { append(value) }
        },
        style = AppFont.sans(T.FsBase),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DataRow(icon: IconDef, onAction: () -> Unit, label: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { label() }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(32.dp).pressable { onAction() },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, size = 24.dp)
        }
    }
}
