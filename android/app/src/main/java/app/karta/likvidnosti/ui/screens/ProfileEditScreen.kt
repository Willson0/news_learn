package app.karta.likvidnosti.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ProfileEditScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf(Session.user?.name ?: "") }
    var tag by remember { mutableStateOf(Session.user?.username ?: "") }
    var avatarUrl by remember { mutableStateOf(Session.user?.avatarUrl) }
    // Локально выбранное фото (предпросмотр). Отправка на сервер появится вместе
    // с общим бэкенд-эндпоинтом загрузки аватара.
    var pickedPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Repository.profile() }.getOrNull()?.user?.let {
            name = it.name ?: name
            tag = it.username ?: tag
            avatarUrl = it.avatarUrl ?: avatarUrl
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedPhoto = uri
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            runCatching { Repository.updateProfile(name.trim(), tag.trim().ifEmpty { null }) }
            runCatching { Session.load(force = true) }
            saving = false
            onDone()
        }
    }

    val initial = (tag.ifEmpty { name }).removePrefix("@").take(1).uppercase()

    Screen(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(T.ShapePill)
                    .background(T.SurfaceMuted)
                    .border(1.dp, Color(0x26FFFFFF), T.ShapePill)
                    .clickable { save() }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            ) { Text(if (saving) "Сохранение…" else "Готово", color = T.Text, fontSize = T.FsBase) }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val url = fileUrl(avatarUrl)
            Box(
                modifier = Modifier.size(148.dp).clip(CircleShape).background(Color(0xFF3F4A2C)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    pickedPhoto != null -> AsyncImage(
                        model = pickedPhoto,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                    url != null -> AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                    initial.isNotEmpty() -> Text(initial, color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Изменить фотографию",
                color = T.Text,
                fontSize = T.FsMd,
                modifier = Modifier.clickable { photoPicker.launch("image/*") },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledInput("Имя", name) { name = it }
            LabeledInput("Тег", tag) { tag = it }
        }
    }
}

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
