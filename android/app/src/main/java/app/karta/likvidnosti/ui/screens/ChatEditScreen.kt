package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.GlassBackButton
import app.karta.likvidnosti.ui.components.ReportCover
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.screenBackground
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * «Чат Инфа Настройки для админа» (Figma 337:36498, 349:12314) — порт iOS
 * `ChatEditView`: как «Профиль изменить» — «назад» и стеклянная пилюля 72×42
 * на y=78, аватар 148 с y=78, «Изменить фотографию» 14, поле «Имя» с
 * подписью (64).
 */
@Composable
fun ChatEditScreen(slug: String, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) picked = uri
    }

    LaunchedEffect(slug) {
        runCatching { Repository.messages(slug) }.getOrNull()?.chat?.let {
            name = it.title
            previewUrl = fileUrl(it.avatarUrl)
        }
    }

    fun save() {
        if (saving) return
        if (name.trim().isEmpty()) { error = "Напишите название"; return }
        saving = true; error = null
        scope.launch {
            try {
                val parts = buildList {
                    add(Uploads.textPart("title", name.trim()))
                    picked?.let { Uploads.filePart(context, "avatar", it)?.let { p -> add(p) } }
                }
                Repository.updateChat(slug, parts)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            } catch (ex: ApiException) {
                error = ex.fieldError("title", "avatar") ?: ex.message
            } catch (ex: Exception) {
                error = "Не удалось сохранить"
            } finally {
                saving = false
            }
        }
    }

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        contentModifier = Modifier.screenBackground(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Клавиатура не закрывает поля: область прокрутки сжимается над ней.
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(78.dp), bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.pressable(pressAlpha = 1f) { picker.launch("image/*") },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val local = picked
                    Box(modifier = Modifier.size(148.dp).clip(CircleShape)) {
                        if (local != null) {
                            AsyncImage(
                                model = local,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            ReportCover(previewUrl, Modifier.fillMaxSize())
                        }
                    }
                    Text("Изменить фотографию", style = AppFont.sans(T.FsBase))
                }

                AuthField(error = error, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    AppInput(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = "Название чата",
                        label = "Имя",
                        error = error != null,
                    )
                }
            }
        },
        overlay = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = designTop(T.TopBarY))
                    .padding(horizontal = T.ScreenX),
                verticalAlignment = Alignment.Top,
            ) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(72.dp, 42.dp)
                        .liquidGlass(CapsuleShape)
                        .pressable(enabled = !saving) { save() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (saving) "…" else "Готово",
                        style = AppFont.sans(13.sp).copy(letterSpacing = 0.52.sp),
                    )
                }
            }
        },
    )
}
