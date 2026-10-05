package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.AdminDto
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.FieldError
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenHeader
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

@Composable
fun AdminsScreen(onBack: () -> Unit, onLeftAdmin: () -> Unit) {
    val admins = remember { mutableStateListOf<AdminDto>() }
    var telegramId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmFor by remember { mutableStateOf<AdminDto?>(null) }
    var removing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myId = Session.user?.telegramId

    suspend fun reload() {
        runCatching { Repository.admins() }.getOrNull()?.let { admins.clear(); admins.addAll(it) }
    }
    LaunchedEffect(Unit) { reload() }

    fun add() {
        val id = telegramId.trim()
        if (!id.matches(Regex("^\\d+$"))) { error = "Telegram ID состоит только из цифр"; return }
        adding = true; error = null
        scope.launch {
            try {
                Repository.addAdmin(id)
                telegramId = ""
                reload()
            } catch (ex: ApiException) {
                error = ex.fieldError("telegram_id") ?: ex.message
            } catch (ex: Exception) {
                error = "Не удалось добавить"
            } finally { adding = false }
        }
    }

    fun confirmRemove() {
        val admin = confirmFor ?: return
        removing = true
        scope.launch {
            try {
                Repository.removeAdmin(admin.telegramId)
                confirmFor = null
                if (admin.telegramId == myId) {
                    runCatching { Session.load(force = true) }
                    onLeftAdmin()
                    return@launch
                }
                reload()
            } catch (ex: Exception) {
                error = (ex as? ApiException)?.message ?: "Не удалось удалить"
                confirmFor = null
            } finally { removing = false }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("Администраторы", onBack = onBack)
            Text(
                "Админы видят админ-панель: создают и редактируют отчёты, настраивают чаты и отвечают в личных чатах. Добавить нового админа можно по его Telegram ID.",
                color = T.TextSecondary, fontSize = T.FsBase,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppInput(telegramId, { telegramId = it; error = null }, placeholder = "Telegram ID", keyboardType = KeyboardType.Number, error = error != null, modifier = Modifier.weight(1f))
                AppButton("Добавить", variant = ButtonVariant.Accent, enabled = !adding) { add() }
            }
            FieldError(error)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                admins.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(T.ShapeChip).background(T.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF8A8A8A)))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.name ?: "Ещё не заходил", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (a.telegramId == myId) Text("(вы)", color = T.TextSecondary, fontSize = T.FsMd)
                            }
                            Text("ID ${a.telegramId}" + (a.username?.let { " · $it" } ?: ""), color = T.TextSecondary, fontSize = T.FsSm)
                        }
                        if (a.root) {
                            Box(modifier = Modifier.clip(T.ShapePill).background(T.GradientAccent).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("Главный", color = T.Text, fontSize = T.FsSm)
                            }
                        } else {
                            Box(
                                modifier = Modifier.clip(T.ShapePill).background(T.GradientPurpleSolid).clickable { confirmFor = a }.padding(horizontal = 12.dp, vertical = 7.dp),
                            ) { Text("Удалить", color = T.Text, fontSize = T.FsBase) }
                        }
                    }
                }
            }
        }

        ConfirmDialog(
            open = confirmFor != null,
            title = "Удалить админа?",
            text = "Он потеряет доступ к админ-панели",
            busy = removing,
            onConfirm = { confirmRemove() },
            onCancel = { confirmFor = null },
        )
    }
}
