package app.karta.likvidnosti.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppToast
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.AvatarCircle
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.GlassTopBar
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.screenBackground
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

/**
 * «Профиль изменить» (iOS ProfileEditView, Figma 337:26931, ошибка 349:12444):
 * «назад» и «Готово» (стекло 72×42) на y=78, между ними аватар 148 с y=78,
 * «Изменить фотографию» 14 через 12, поля «Имя» и «Тег» с подписью (64,
 * через 12) с отступом 24. Долгое нажатие на фото — «Удалить фото» (как
 * контекстное меню в iOS). Ошибка тега («Тег уже занят») — под полем «Тег».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit, onDone: () -> Unit) {
    var name by remember { mutableStateOf(Session.user?.name ?: "") }
    var tag by remember { mutableStateOf(Session.user?.username ?: "") }
    var avatarUrl by remember { mutableStateOf(Session.user?.avatarUrl) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        runCatching { Repository.profile() }.getOrNull()?.user?.let {
            name = it.name ?: name
            tag = it.username ?: tag
            avatarUrl = it.avatarUrl
        }
    }

    fun uploadPhoto(uri: android.net.Uri) {
        if (uploading) return
        uploading = true
        scope.launch {
            val part = Uploads.filePart(context, "avatar", uri)
            if (part != null) {
                runCatching { Repository.uploadAvatar(part) }
                    .onSuccess { r -> r?.let { avatarUrl = it.avatarUrl } }
                    .onFailure { AppToast.show("Не удалось загрузить фото") }
                runCatching { Session.load(force = true) }
            }
            uploading = false
        }
    }

    fun removePhoto() {
        if (uploading) return
        uploading = true
        scope.launch {
            runCatching { Repository.removeAvatar() }
            avatarUrl = null
            runCatching { Session.load(force = true) }
            uploading = false
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadPhoto(uri)
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                Repository.updateProfile(name.trim(), tag.trim().ifEmpty { null })
                runCatching { Session.load(force = true) }
                onDone()
            } catch (ex: ApiException) {
                tagError = ex.fieldError("username")?.let { tagErrorText(it) } ?: ex.message
            } catch (ex: Exception) {
                tagError = "Не удалось сохранить"
            } finally {
                saving = false
            }
        }
    }

    val initial = tag.replace("@", "").ifEmpty { name }.take(1).uppercase()

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        contentModifier = Modifier.screenBackground(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(T.TopBarY), bottom = designBottom(40.dp)),
            ) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Column(
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !uploading,
                            onLongClick = { if (avatarUrl != null) menuOpen = true },
                            onClick = { photoPicker.launch("image/*") },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AvatarCircle(url = fileUrl(avatarUrl), size = 148.dp, initial = initial)
                            if (uploading) {
                                Box(Modifier.size(148.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)))
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            }
                        }
                        Text(if (uploading) "Загрузка…" else "Изменить фотографию", style = AppFont.sans(T.FsBase))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Удалить фото", style = AppFont.sans(T.FsBase, color = T.Error)) },
                            onClick = {
                                menuOpen = false
                                removePhoto()
                            },
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppInput(name, { name = it }, placeholder = "например, Иван", label = "Имя")
                    AuthField(tagError) {
                        AppInput(
                            tag,
                            { tag = it; tagError = null },
                            placeholder = "например, ivan_trader",
                            label = "Тег",
                            error = tagError != null,
                        )
                    }
                }
            }
        },
        overlay = {
            GlassTopBar(
                onBack = onBack,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 42.dp)
                            .liquidGlass(CapsuleShape)
                            .pressable(enabled = !saving) { save() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (saving) "…" else "Готово",
                            style = AppFont.sans(13.sp).copy(letterSpacing = 0.52.sp),
                        )
                    }
                },
            )
        },
    )
}

/** Старое поле с подписью (используется экраном изменения чата, ещё не перерисованным). */
@Composable
fun LabeledInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(T.ShapeInput)
            .background(T.SurfaceMuted)
            .border(1.dp, Color(0x14FFFFFF), T.ShapeInput)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = T.TextSecondary, fontSize = T.FsSm)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = T.Text, fontSize = T.FsMd),
            cursorBrush = SolidColor(T.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
