package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.api.MessageDto
import app.karta.likvidnosti.ui.components.ChatMessage
import app.karta.likvidnosti.ui.components.MessageBubble
import app.karta.likvidnosti.ui.components.insetBottom
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.launch

private val REACTIONS = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "👏")

private val GROUP_MESSAGES = listOf(
    ChatMessage(1, false, author = "Артем", role = "Участник", text = "Здравствуте! Помогите разобраться с графиком", time = "20:41"),
    ChatMessage(2, true, text = "А то никак понять не могу", time = "20:42", read = true),
    ChatMessage(3, false, author = "Владимир", role = "Участник", replyAuthor = "Алексей", replyText = "Я сам немного не оче...", text = "Ага, сам не понял, но видно, что очень хорошо сделан отчет и много чего было все равно было объяснено русским языком", time = "20:42", reactions = listOf("👍" to 2)),
    ChatMessage(4, true, voiceDuration = "10:42", time = "20:42", read = true),
    ChatMessage(5, false, author = "Артем", role = "Участник", voiceDuration = "00:42", time = "20:42"),
    ChatMessage(6, false, author = "Артем", role = "Участник", text = "Здравствуте! Помогите разобраться с графиком", fileExt = "zip", fileName = "zip file for windows", fileSize = "1.9 мб", time = "20:42"),
    ChatMessage(7, true, image = true, time = "20:42", read = true),
    ChatMessage(8, true, toAuthor = true, text = "«Как долго вы делали этот отчет?»", time = "20:42", read = true),
)

private val DIRECT_MESSAGES = listOf(
    ChatMessage(1, false, text = "Здравствуйте! Чем могу помочь?", time = "16:30"),
    ChatMessage(2, true, text = "Здравствуйте! Не получается оплатить подписку", time = "16:31", read = true),
    ChatMessage(3, false, text = "Подскажите, пожалуйста, какой способ оплаты выбираете?", time = "16:31"),
    ChatMessage(4, true, text = "СБП", time = "16:32", read = true),
    ChatMessage(5, false, text = "Что интересует?", time = "16:32"),
)

private fun MessageDto.toUi() = ChatMessage(id = id, mine = mine, author = author, role = role, text = text, time = time)

@Composable
fun ChatScreen(
    slug: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onUser: (Long) -> Unit,
) {
    val isDirect = slug == "admin" || slug.startsWith("dm-")
    val serverMessages = remember { mutableStateListOf<ChatMessage>() }
    var usingServer by remember { mutableStateOf(false) }
    var chatTitle by remember { mutableStateOf(if (isDirect) "Игорь Гломозда" else "Нефть 12.12.27") }
    var chatSubtitle by remember { mutableStateOf(if (isDirect) "Был в сети 1 час назад" else "15 участников") }

    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var attachOpen by remember { mutableStateOf(false) }
    var pinnedVisible by remember { mutableStateOf(true) }
    var photoViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slug) {
        runCatching { Repository.messages(slug) }.getOrNull()?.let { resp ->
            resp.chat?.let { chatTitle = it.title; it.subtitle?.let { s -> chatSubtitle = s } }
            serverMessages.clear()
            serverMessages.addAll(resp.data.map { it.toUi() })
            usingServer = true
        }
    }

    val messages: List<ChatMessage> = if (usingServer) serverMessages else if (isDirect) DIRECT_MESSAGES else GROUP_MESSAGES
    val showSend = draft.trim().isNotEmpty() || replyTo != null
    val showMentions = draft.contains("@")

    fun send() {
        val body = draft.trim()
        draft = ""; replyTo = null
        if (body.isEmpty()) return
        if (usingServer) {
            scope.launch {
                runCatching { Repository.sendMessage(slug, body) }.getOrNull()?.let { serverMessages.add(it.toUi()) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Bg)
            .background(
                Brush.radialGradient(
                    0f to Color(0x66463746),
                    1f to Color(0x001A1B1D),
                    center = androidx.compose.ui.geometry.Offset(540f, 0f),
                    radius = 1200f,
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // Header
            if (!selectMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = T.ScreenX, end = T.ScreenX, top = insetTop() + 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(T.ShapePill)
                            .background(T.SurfaceMuted)
                            .border(1.dp, Color(0x14FFFFFF), T.ShapePill)
                            .clickable { onInfo() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(chatTitle, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
                            Text(chatSubtitle, color = T.TextSecondary, fontSize = T.FsSm)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFCFC6D6), Color(0xFFA9ADBD), Color(0xFFB7C2BF))))
                            .clickable { onInfo() },
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = T.ScreenX, end = T.ScreenX, top = insetTop() + 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill("Выбрано ${selected.size}")
                    Box(modifier = Modifier.clickable { selectMode = false; selected.clear() }) {
                        Pill("Отмена")
                    }
                }
            }

            // Pinned
            if (pinnedVisible && !selectMode && !isDirect) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = T.ScreenX)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(T.SurfaceMuted)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Закрепленное сообщение", color = T.Text, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                        Text("Здравствуте! Помогите разобраться...", color = T.TextSecondary, fontSize = T.FsSm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AppIcon(AppIcons.Close, size = 18.dp, modifier = Modifier.clickable { pinnedVisible = false })
                }
            }

            // Messages
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = T.ScreenX),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.clip(T.ShapePill).background(T.SurfaceMuted).padding(horizontal = 14.dp, vertical = 4.dp),
                        ) { Text("Сегодня", color = T.TextSecondary, fontSize = T.FsSm) }
                    }
                }
                items(messages, key = { it.id }) { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectMode) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, if (selected.contains(m.id)) T.Green else Color(0x59FFFFFF), CircleShape)
                                    .background(if (selected.contains(m.id)) T.Green else Color.Transparent)
                                    .clickable {
                                        if (selected.contains(m.id)) selected.remove(m.id) else selected.add(m.id)
                                    },
                            )
                        }
                        if (!m.mine && !selectMode && !isDirect) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8A8A8A))
                                    .clickable { onUser(0) },
                            )
                        }
                        MessageBubble(
                            m,
                            modifier = Modifier.clickable {
                                when {
                                    selectMode -> if (selected.contains(m.id)) selected.remove(m.id) else selected.add(m.id)
                                    m.image -> photoViewer = true
                                    else -> menuFor = m
                                }
                            },
                        )
                    }
                }
            }

            // Composer / select actions
            if (selectMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoundBtn(AppIcons.Trash, danger = true) {}
                    RoundBtn(AppIcons.Forward) {}
                }
            } else {
                Composer(
                    draft = draft,
                    onDraft = { draft = it },
                    showSend = showSend,
                    attachOpen = attachOpen,
                    onToggleAttach = { attachOpen = !attachOpen },
                    showMentions = showMentions,
                    onPickMention = { handle -> draft = draft.replace(Regex("@\\S*$"), "$handle ") },
                    replyTo = replyTo,
                    onCancelReply = { replyTo = null },
                    onSend = { send() },
                )
            }
        }

        // Context menu
        menuFor?.let { msg ->
            MessageMenu(
                message = msg,
                onClose = { menuFor = null },
                onReply = { replyTo = msg; menuFor = null },
                onSelect = { selectMode = true; selected.add(msg.id); menuFor = null },
            )
        }

        // Photo viewer
        if (photoViewer) {
            PhotoViewer(onClose = { photoViewer = false })
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier.clip(T.ShapePill).background(T.SurfaceMuted).border(1.dp, Color(0x14FFFFFF), T.ShapePill).padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, color = T.Text, fontSize = T.FsBase) }
}

@Composable
private fun RoundBtn(res: Int, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (danger) T.Danger else T.SurfaceMuted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AppIcon(res, size = 22.dp, tint = Color.White) }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    showSend: Boolean,
    attachOpen: Boolean,
    onToggleAttach: () -> Unit,
    showMentions: Boolean,
    onPickMention: (String) -> Unit,
    replyTo: ChatMessage?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
) {
    val mentions = listOf("Владислав" to "@Submetal", "Артем" to "@Submetalllliiist", "Влад" to "@Submetaliiist")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 12.dp)) {
        if (attachOpen) {
            Column(
                modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(T.Surface).padding(6.dp),
            ) {
                AttachItem(AppIcons.Doc, "Изображение") { onToggleAttach() }
                AttachItem(AppIcons.Doc, "Файл") { onToggleAttach() }
            }
        }
        if (showMentions) {
            Column(modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(T.Surface).padding(4.dp)) {
                mentions.forEach { (name, handle) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPickMention(handle) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF8A8A8A)))
                        Text(name, color = T.Text, fontSize = T.FsBase)
                        Text(handle, color = T.TextSecondary, fontSize = T.FsSm)
                    }
                }
            }
        }
        if (replyTo != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(AppIcons.Reply, size = 18.dp, tint = T.TextSecondary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(replyTo.author ?: "Вы", color = T.Text, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                    Text(replyTo.text ?: "Сообщение", color = T.TextSecondary, fontSize = T.FsSm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AppIcon(AppIcons.Close, size = 18.dp, modifier = Modifier.clickable { onCancelReply() })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(T.SurfaceMuted).clickable { onToggleAttach() },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Plus, size = 24.dp) }
            Box(
                modifier = Modifier.weight(1f).height(44.dp).clip(T.ShapePill).background(T.SurfaceMuted).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text("Напишите что-нибудь", color = T.TextMuted, fontSize = T.FsBase)
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = T.Text, fontSize = T.FsBase),
                    cursorBrush = SolidColor(T.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).then(if (showSend) Modifier.background(T.GradientAccent) else Modifier.background(T.SurfaceMuted)).clickable { if (showSend) onSend() },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(if (showSend) AppIcons.Forward else AppIcons.Mic, size = 22.dp, tint = Color.White)
            }
        }
    }
}

@Composable
private fun AttachItem(res: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIcon(res, size = 22.dp)
        Text(label, color = T.Text, fontSize = T.FsBase)
    }
}

@Composable
private fun MessageMenu(
    message: ChatMessage,
    onClose: () -> Unit,
    onReply: () -> Unit,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = T.ScreenX),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.clip(T.ShapePill).background(T.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                REACTIONS.forEach { Text(it, fontSize = T.FsMd) }
            }
            MessageBubble(message)
            Column(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(T.Surface).width(240.dp),
            ) {
                MenuItem(AppIcons.Reply, "Ответить", onClick = onReply)
                MenuItem(AppIcons.Copy, "Скопировать", onClick = onClose)
                MenuItem(AppIcons.Pin, "Закрепить", onClick = onClose)
                MenuItem(AppIcons.Link, "Копировать ссылку", onClick = onClose)
                MenuItem(AppIcons.Report, "Пожаловаться", onClick = onClose)
                MenuItem(AppIcons.Trash, "Удалить", danger = true, onClick = onClose)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0FFFFFFF)))
                MenuItem(AppIcons.CheckSquare, "Выбрать", onClick = onSelect)
            }
        }
    }
}

@Composable
private fun MenuItem(res: Int, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(res, size = 20.dp, tint = if (danger) T.Error else T.Text)
        Text(label, color = if (danger) T.Error else T.Text, fontSize = T.FsBase)
    }
}

@Composable
private fun PhotoViewer(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xF2000000))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = T.ScreenX, end = T.ScreenX, top = insetTop() + 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(AppIcons.Back, size = 24.dp, tint = Color.White, modifier = Modifier.clickable { onClose() })
            Column(modifier = Modifier.weight(1f)) {
                Text("Вы", color = Color.White, fontSize = T.FsBase, fontWeight = FontWeight.Bold)
                Text("Сегодня в 1:42", color = T.TextSecondary, fontSize = T.FsSm)
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFCFC6D6), Color(0xFFA9ADBD), Color(0xFFB7C2BF)))),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundBtn(AppIcons.Forward) {}
            RoundBtn(AppIcons.Trash, danger = true) {}
        }
    }
}
