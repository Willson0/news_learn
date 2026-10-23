package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.AdminDto
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthFieldError
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.InnerScreen
import app.karta.likvidnosti.ui.components.PurplePillButton
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.hex
import kotlinx.coroutines.launch

/**
 * «Администраторы» (iOS AdminsView): заголовок на y=160, пояснение 14 белый
 * 65 %, поле Telegram ID со стеклянной кнопкой «Добавить» («active», 48),
 * карточки админов (#25262A, радиус 24, ≥64): серый круг 30, имя SemiBold 12
 * и «ID … · тег» 12; справа «Главный» («active») или пурпурная «Удалить» с
 * подтверждением.
 */
@Composable
fun AdminsScreen(onBack: () -> Unit, onLeftAdmin: () -> Unit) {
    val admins = remember { mutableStateListOf<AdminDto>() }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmFor by remember { mutableStateOf<AdminDto?>(null) }
    var removing by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val myId = Session.user?.telegramId

    suspend fun reload() {
        try {
            val list = Repository.admins()
            admins.clear()
            admins.addAll(list)
        } catch (ex: ApiException) {
            error = ex.message
        } catch (ex: Exception) {
            error = "Не удалось загрузить список"
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun add() {
        val value = email.trim()
        if (!value.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            error = "Укажите почту пользователя"
            return
        }
        if (adding) return
        adding = true
        error = null
        scope.launch {
            try {
                Repository.addAdmin(value)
                email = ""
                reload()
            } catch (ex: ApiException) {
                error = ex.fieldError("email") ?: ex.message
            } catch (ex: Exception) {
                error = "Не удалось добавить"
            } finally {
                adding = false
            }
        }
    }

    fun confirmRemove() {
        val admin = confirmFor ?: return
        val adminId = admin.id ?: return
        removing = adminId
        scope.launch {
            try {
                Repository.removeAdmin(adminId)
                confirmFor = null
                if (admin.telegramId == myId) {
                    runCatching { Session.load(force = true) }
                    onLeftAdmin()
                } else {
                    reload()
                }
            } catch (ex: ApiException) {
                error = ex.message
                confirmFor = null
            } catch (ex: Exception) {
                error = "Не удалось удалить"
                confirmFor = null
            } finally {
                removing = null
            }
        }
    }

    InnerScreen(
        onBack = onBack,
        bottomPadding = 32.dp,
        spacing = 12.dp,
        overlay = {
            ConfirmDialog(
                open = confirmFor != null,
                title = "Удалить админа?",
                text = "Он потеряет доступ к админ-панели",
                busy = removing != null,
                onConfirm = { confirmRemove() },
                onCancel = { confirmFor = null },
            )
        },
    ) {
        Text("Администраторы", style = AppFont.heading(T.FsTitle))
        Text(
            "Админы видят админ-панель: создают и редактируют отчёты, настраивают чаты и отвечают в личных чатах. Добавить нового админа можно по его почте, с которой он зарегистрирован.",
            style = AppFont.sans(T.FsBase, color = T.TextSecondary).copy(lineHeight = 19.sp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppInput(
                email,
                { email = it; error = null },
                placeholder = "Почта пользователя",
                error = error != null,
                keyboardType = KeyboardType.Email,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .alpha(if (adding) 0.5f else 1f)
                    .liquidGlass(T.ShapeInput, Fills.Active)
                    .pressable(enabled = !adding) { add() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Добавить", style = AppFont.sans(T.FsBase))
            }
        }

        error?.let { AuthFieldError(it) }

        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            admins.forEach { a -> AdminRow(a, isMe = a.telegramId == myId, removing = removing == a.id) { confirmFor = a } }
        }
    }
}

@Composable
private fun AdminRow(a: AdminDto, isMe: Boolean, removing: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(T.ShapeCard)
            .background(T.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(hex("#d9d9d9")))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    a.name ?: "Ещё не заходил",
                    style = AppFont.sans(T.FsSm, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isMe) Text("(вы)", style = AppFont.sans(T.FsSm, color = T.TextSecondary))
            }
            Text(
                a.email ?: a.username ?: "Без почты",
                style = AppFont.sans(T.FsSm, color = T.TextSecondary),
            )
        }
        if (a.root) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .liquidGlass(CapsuleShape, Fills.Active)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Главный", style = AppFont.sans(13.sp).copy(letterSpacing = 0.52.sp))
            }
        } else {
            PurplePillButton("Удалить", modifier = Modifier.alpha(if (removing) 0.5f else 1f)) {
                if (!removing) onRemove()
            }
        }
    }
}
