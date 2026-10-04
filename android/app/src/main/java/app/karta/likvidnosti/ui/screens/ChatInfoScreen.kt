package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage

private data class InfoMember(val name: String, val seen: String, val role: String)
private data class InfoFile(val ext: String, val color: Color, val name: String, val meta: String)

@Composable
fun ChatInfoScreen(slug: String, onBack: () -> Unit, onEdit: () -> Unit) {
    var title by remember { mutableStateOf("Нефть 12.12.27") }
    var members by remember { mutableStateOf("15 участников") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("media") }
    val isAdmin = Session.isAdmin

    LaunchedEffect(slug) {
        runCatching { Repository.messages(slug) }.getOrNull()?.chat?.let {
            title = it.title.ifEmpty { title }
            members = it.subtitle ?: members
            avatar = fileUrl(it.avatarUrl)
        }
    }

    val memberList = remember {
        listOf(
            InfoMember("Владислав", "Был в сети 58 минут назад", "Автор"),
            InfoMember("Алексей", "Был в сети 15 минут назад", "Участник"),
            InfoMember("Борат", "Был в сети 1 час назад", "Участник"),
            InfoMember("Владимир", "Был в сети 2 часа назад", "Участник"),
            InfoMember("Артем в2", "Был в сети 50 минут назад", "Участник"),
        )
    }
    val files = remember {
        listOf(
            InfoFile("pdf", Color(0xFFE8503A), "pdf file for windows", "1.9 мб / 6 сент. 2026 в 8:32"),
            InfoFile("exe", Color(0xFF3A7BE8), "pdf file for windows", "1.9 мб / 6 сент. 2026 в 8:32"),
            InfoFile("zip", Color(0xFF7FBF1F), "pdf file for windows", "1.9 мб / 6 сент. 2026 в 8:32"),
            InfoFile("", Color(0xFFCFCFCF), "Image0340", "1.9 мб / 6 сент. 2026 в 8:32"),
        )
    }
    val tabs = listOf("members" to "Участники", "media" to "Медиа", "voice" to "Голосовые", "files" to "Файлы", "links" to "Ссылки")

    Column(
        modifier = Modifier.fillMaxSize().background(T.Bg).verticalScroll(rememberScrollState()),
    ) {
        // Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF343235), Color(0xFF1A1B1D))))
                .padding(top = app.karta.likvidnosti.ui.components.insetTop() + 16.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(start = T.ScreenX).size(42.dp).clip(CircleShape).background(T.SurfaceMuted).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Back, size = 22.dp) }

            if (isAdmin) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = T.ScreenX).size(42.dp).clip(CircleShape).background(T.SurfaceMuted).clickable { onEdit() },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.Gear, size = 22.dp) }
            } else {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = T.ScreenX).size(42.dp).clip(CircleShape).background(T.SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.Contact, size = 22.dp) }
            }

            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val av = avatar
                Box(
                    modifier = Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFD7CFE0), Color(0xFFA9ADBD), Color(0xFFB7C2BF)))),
                ) {
                    if (av != null) AsyncImage(model = av, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
                Text(title, color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(members, color = T.TextSecondary, fontSize = T.FsBase)
            }
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth().padding(start = T.ScreenX, end = T.ScreenX, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AppIcons.Bell, AppIcons.Search, AppIcons.Leave).forEach { ic ->
                Box(
                    modifier = Modifier.weight(1f).height(52.dp).clip(T.ShapeCard).background(T.Surface),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(ic, size = 22.dp) }
            }
        }

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(T.ScreenX).clip(T.ShapeCard).background(T.SurfaceMuted).horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { (v, label) ->
                val active = tab == v
                Box(
                    modifier = Modifier.height(40.dp).clip(T.ShapeChip).then(if (active) Modifier.background(T.GradientAccentPurple) else Modifier).clickable { tab = v }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = T.Text, fontSize = T.FsBase) }
            }
        }

        // Content
        Column(modifier = Modifier.fillMaxWidth().padding(start = T.ScreenX, end = T.ScreenX, bottom = 24.dp)) {
            when (tab) {
                "members" -> memberList.forEach { m ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF8A8A8A)))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                            Text(m.seen, color = T.TextSecondary, fontSize = T.FsBase, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(m.role, color = T.TextSecondary, fontSize = T.FsBase)
                    }
                }
                "media" -> {
                    (0 until 3).forEach { r ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (0 until 3).forEach { _ ->
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(top = 2.dp).background(Color(0xFFD4D4D4)))
                            }
                        }
                    }
                }
                "voice" -> (0 until 6).forEach {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF4E8F10)), contentAlignment = Alignment.Center) { AppIcon(AppIcons.Play, size = 18.dp, tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text("Владислав", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                            Text("20:42 / 6 сент. 2026 в 8:32", color = T.TextSecondary, fontSize = T.FsBase)
                        }
                    }
                }
                "files" -> files.forEach { f ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(f.color), contentAlignment = Alignment.Center) {
                            Text(f.ext, color = Color.White, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                            Text(f.meta, color = T.TextSecondary, fontSize = T.FsBase, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AppIcon(AppIcons.DownloadCloud, size = 24.dp, tint = T.TextSecondary)
                    }
                }
                else -> {
                    LinkRow("Telegram", "Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!", "rbk.ru/news")
                    Text("Август 2026", color = T.TextSecondary, fontSize = T.FsBase, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    LinkRow("Telegram", "Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!", "rbk.ru/news")
                    LinkRow("Telegram", "Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!", "rbk.ru/news")
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, text: String, url: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(T.Surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF8A8A8A)))
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
            Text(text, color = T.TextSecondary, fontSize = T.FsBase, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
            Text(url, color = Color(0xFF6EA8E0), fontSize = T.FsBase)
        }
    }
}
