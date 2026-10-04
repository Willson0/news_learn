package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.FieldError
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ChatEditScreen(slug: String, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
        if (name.trim().isEmpty()) { error = "Напишите название"; return }
        saving = true; error = null
        scope.launch {
            try {
                val parts = buildList {
                    add(Uploads.textPart("title", name.trim()))
                    picked?.let { Uploads.filePart(context, "avatar", it)?.let { p -> add(p) } }
                }
                Repository.updateChat(slug, parts)
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

    Screen(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(T.ShapePill).background(T.SurfaceMuted).border(1.dp, Color(0x26FFFFFF), T.ShapePill)
                    .clickable(enabled = !saving) { save() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) { Text("Готово", color = T.Text, fontSize = T.FsBase) }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val model: Any? = picked ?: previewUrl
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFD7CFE0), Color(0xFFA9ADBD), Color(0xFFB7C2BF))))
                    .clickable { picker.launch("image/*") },
            ) {
                if (model != null) {
                    AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
            }
            Text("Изменить фотографию", color = T.Text, fontSize = T.FsMd, modifier = Modifier.clickable { picker.launch("image/*") })
        }

        LabeledInput("Название", name) { name = it; error = null }
        FieldError(error)
    }
}
