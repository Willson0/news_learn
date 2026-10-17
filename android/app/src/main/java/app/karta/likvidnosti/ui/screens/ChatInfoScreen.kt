package app.karta.likvidnosti.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ChatAttachmentDto
import app.karta.likvidnosti.data.api.ParticipantDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage

@Composable
fun ChatInfoScreen(slug: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("members") }
    val isAdmin = Session.isAdmin

    val participants = remember { mutableStateListOf<ParticipantDto>() }
    val media = remember { mutableStateListOf<ChatAttachmentDto>() }
    val voices = remember { mutableStateListOf<ChatAttachmentDto>() }
    val files = remember { mutableStateListOf<ChatAttachmentDto>() }
    val links = remember { mutableStateListOf<ChatAttachmentDto>() }
    val loaded = remember { mutableStateListOf<String>() }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }

    fun stopPlayer() {
        player?.runCatching { stop() }
        player?.release(); player = null; playingId = null
    }
    fun playVoice(v: ChatAttachmentDto) {
        val url = fileUrl(v.url) ?: return
        if (playingId == v.id) { stopPlayer(); return }
        stopPlayer()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start(); playingId = v.id }
                setOnCompletionListener { stopPlayer() }
                setOnErrorListener { _, _, _ -> stopPlayer(); true }
                prepareAsync()
            }
        }
    }
    fun openUrl(url: String?) {
        val u = url ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }

    DisposableEffect(Unit) { onDispose { player?.release() } }

    LaunchedEffect(slug) {
        runCatching { Repository.messages(slug) }.getOrNull()?.chat?.let {
            title = it.title.ifEmpty { title }
            members = it.subtitle ?: members
            avatar = fileUrl(it.avatarUrl)
        }
    }

    LaunchedEffect(tab) {
        if (loaded.contains(tab)) return@LaunchedEffect
        when (tab) {
            "members" -> runCatching { Repository.participants(slug) }.getOrNull()?.let {
                participants.clear(); participants.addAll(it)
                if (members.isEmpty() && it.isNotEmpty()) members = "${it.size} участников"
            }
            "media" -> runCatching { Repository.chatAttachments(slug, "image") }.getOrNull()?.let { media.clear(); media.addAll(it) }
            "voice" -> runCatching { Repository.chatAttachments(slug, "voice") }.getOrNull()?.let { voices.clear(); voices.addAll(it) }
            "files" -> runCatching { Repository.chatAttachments(slug, "file") }.getOrNull()?.let { files.clear(); files.addAll(it) }
            "links" -> runCatching { Repository.chatAttachments(slug, "link") }.getOrNull()?.let { links.clear(); links.addAll(it) }
        }
        loaded.add(tab)
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
                "members" -> {
                    if (participants.isEmpty()) EmptyRow("Пока нет участников")
                    participants.forEach { m ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val av = m.avatarUrl
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF8A8A8A))) {
                                if (!av.isNullOrEmpty()) AsyncImage(model = av, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                                Text(m.seen ?: "", color = T.TextSecondary, fontSize = T.FsBase, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(m.role ?: "", color = T.TextSecondary, fontSize = T.FsBase)
                        }
                    }
                }
                "media" -> {
                    if (media.isEmpty()) EmptyRow("Пока нет медиа")
                    media.chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { a ->
                                AsyncImage(
                                    model = fileUrl(a.url),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).aspectRatio(1f).background(Color(0xFF2A2B2E)).clickable { openUrl(fileUrl(a.url)) },
                                )
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
                        }
                    }
                }
                "voice" -> {
                    if (voices.isEmpty()) EmptyRow("Пока нет голосовых")
                    voices.forEach { v ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF4E8F10)).clickable { playVoice(v) },
                                contentAlignment = Alignment.Center,
                            ) { AppIcon(AppIcons.Play, size = 18.dp, tint = if (playingId == v.id) Color(0xFFBFF08A) else Color.White) }
                            Column(Modifier.weight(1f)) {
                                Text(v.author ?: "Голосовое", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                                Text(v.meta ?: "", color = T.TextSecondary, fontSize = T.FsBase)
                            }
                        }
                    }
                }
                "files" -> {
                    if (files.isEmpty()) EmptyRow("Пока нет файлов")
                    files.forEach { f ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { openUrl(fileUrl(f.url)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A7BE8)), contentAlignment = Alignment.Center) {
                                Text(f.ext ?: "", color = Color.White, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(f.name ?: "Файл", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                                Text(f.meta ?: "", color = T.TextSecondary, fontSize = T.FsBase, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            AppIcon(AppIcons.DownloadCloud, size = 24.dp, tint = T.TextSecondary)
                        }
                    }
                }
                else -> {
                    if (links.isEmpty()) EmptyRow("Пока нет ссылок")
                    var lastMonth: String? = null
                    links.forEach { l ->
                        if (!l.month.isNullOrEmpty() && l.month != lastMonth) {
                            lastMonth = l.month
                            Text(l.month!!, color = T.TextSecondary, fontSize = T.FsBase, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                        LinkRow(l.title ?: l.url, l.text ?: "", l.url) { openUrl(l.url) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, color = T.TextSecondary, fontSize = T.FsBase, modifier = Modifier.padding(vertical = 20.dp))
}

@Composable
private fun LinkRow(title: String, text: String, url: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(T.Surface).clickable { onClick() }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF8A8A8A)))
        Column(Modifier.weight(1f)) {
            Text(title, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
            if (text.isNotEmpty()) Text(text, color = T.TextSecondary, fontSize = T.FsBase, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(url, color = Color(0xFF6EA8E0), fontSize = T.FsBase, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
