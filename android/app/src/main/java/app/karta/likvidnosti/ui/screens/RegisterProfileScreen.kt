package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.AuthMessageView
import app.karta.likvidnosti.ui.components.AuthScaffold
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.RemoteImage
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.hex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ошибка тега от бэкенда → текст под полем «Тег» (iOS: «Тег уже занят»). */
internal fun tagErrorText(message: String): String =
    if (message.lowercase().contains("taken") || message.contains("занят")) "Тег уже занят" else message

/**
 * «Регистрация», шаг 2 (iOS RegisterProfileView, Figma 337:26682, ошибка
 * 349:9071, приветствие 337:26694): аватар 148 (#D9D9D9) на y=94,
 * «Изменить фотографию» 14, поля «Имя» и «Тег» с подписью (64, через 12,
 * отступ 24), внизу правовой текст 14 белый 40 % со ссылкой белым и
 * «Зарегистрироваться». После сохранения — «Приветствуем, <имя>!» и вход в
 * приложение.
 */
@Composable
fun RegisterProfileScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf(Session.user?.name ?: "") }
    var tag by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf(Session.user?.avatarUrl) }
    var uploading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var greeting by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Шаг обязательный: системное «назад» не возвращает к коду подтверждения.
    BackHandler {}

    val greetName = greeting
    if (greetName != null) {
        LaunchedEffect(Unit) {
            delay(1600)
            onDone()
        }
        AuthMessageView("Приветствуем, $greetName!")
        return
    }

    fun upload(uri: Uri) {
        if (uploading) return
        uploading = true
        scope.launch {
            val part = Uploads.filePart(context, "avatar", uri)
            if (part != null) {
                runCatching { Repository.uploadAvatar(part) }.getOrNull()?.let { avatarUrl = it.avatarUrl }
            }
            uploading = false
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) upload(uri)
    }

    fun submit() {
        val n = name.trim()
        val t = tag.trim().replace("@", "")
        val e = mutableMapOf<String, String>()
        if (n.isEmpty()) e["name"] = "Введите имя"
        if (t.isEmpty()) e["username"] = "Придумайте тег"
        errors = e
        if (e.isNotEmpty() || saving) return
        saving = true
        scope.launch {
            try {
                Repository.updateProfile(n, t)
                runCatching { Session.load(force = true) }
                greeting = n
            } catch (ex: ApiException) {
                val tagError = ex.fieldError("username")
                val nameError = ex.fieldError("name")
                errors = when {
                    tagError != null -> mapOf("username" to tagErrorText(tagError))
                    nameError != null -> mapOf("name" to nameError)
                    else -> mapOf("username" to ex.message)
                }
            } catch (ex: Exception) {
                errors = mapOf("username" to "Не удалось сохранить")
            } finally {
                saving = false
            }
        }
    }

    AuthScaffold(
        showBack = false,
        contentTop = 94.dp,
        buttons = {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f))) {
                        append("Нажимая на кнопку «Зарегистрироваться», Вы соглашаетесь с ")
                    }
                    withStyle(SpanStyle(color = T.Text)) { append("Правовой документацией") }
                },
                style = AppFont.sans(T.FsBase),
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton("Зарегистрироваться", variant = ButtonVariant.Accent, enabled = !saving && !uploading) { submit() }
        },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .pressable(enabled = !uploading) { photoPicker.launch("image/*") },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(148.dp).clip(CircleShape).background(hex("#d9d9d9")),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(fileUrl(avatarUrl), Modifier.fillMaxSize().clip(CircleShape))
                if (uploading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
            Text("Изменить фотографию", style = AppFont.sans(T.FsBase))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuthField(errors["name"]) {
                AppInput(
                    name,
                    { name = it; if (errors.containsKey("name")) errors = errors - "name" },
                    placeholder = "Введите имя",
                    label = "Имя",
                    error = errors.containsKey("name"),
                )
            }
            AuthField(errors["username"]) {
                AppInput(
                    tag,
                    { tag = it; if (errors.containsKey("username")) errors = errors - "username" },
                    placeholder = "Придумайте тег",
                    label = "Тег",
                    error = errors.containsKey("username"),
                )
            }
        }
    }
}
