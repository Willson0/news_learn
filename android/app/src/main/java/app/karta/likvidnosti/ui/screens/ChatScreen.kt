package app.karta.likvidnosti.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.MessageDto
import app.karta.likvidnosti.data.api.MessageQuoteDto
import app.karta.likvidnosti.data.api.ParticipantDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.ChatMessage
import app.karta.likvidnosti.ui.components.MessageBubble
import app.karta.likvidnosti.ui.components.Reaction
import app.karta.likvidnosti.ui.components.insetBottom
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

private val REACTIONS = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "👏")

private fun MessageDto.toUi() = ChatMessage(
    id = id,
    mine = mine,
    userId = userId,
    author = author,
    role = role,
    text = text,
    time = time,
    read = read,
    voiceUrl = voice?.url,
    voiceDuration = voice?.duration,
    fileUrl = file?.url,
    fileExt = file?.ext,
    fileName = file?.name,
    fileSize = file?.size,
    imageUrl = image?.url,
    imageWidth = image?.width,
    imageHeight = image?.height,
    replyAuthor = reply?.author,
    replyText = reply?.text,
    reactions = reactions.map { Reaction(it.emoji, it.count, it.mine) },
    pinned = pinned,
)

@Composable
fun ChatScreen(
    slug: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onUser: (Long) -> Unit,
) {
    val context = LocalContext.current
    val isDirect = slug == "admin" || slug.startsWith("dm-")
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var chatTitle by remember { mutableStateOf("") }
    var chatSubtitle by remember { mutableStateOf("") }
    var pinnedQuote by remember { mutableStateOf<MessageQuoteDto?>(null) }
    val participants = remember { mutableStateListOf<ParticipantDto>() }

    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var attachOpen by remember { mutableStateOf(false) }
    var viewerMsg by remember { mutableStateOf<ChatMessage?>(null) }

    // Запись голосового
    var recState by remember { mutableStateOf("idle") } // idle | recording
    var recSeconds by remember { mutableStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<File?>(null) }

    // Воспроизведение голосового
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun reload() {
        runCatching { Repository.messages(slug) }.getOrNull()?.let { resp ->
            resp.chat?.let { chatTitle = it.title; chatSubtitle = it.subtitle ?: "" }
            messages.clear()
            messages.addAll(resp.data.map { it.toUi() })
            pinnedQuote = resp.pinned
        }
        runCatching { Repository.participants(slug) }.getOrNull()?.let {
            participants.clear(); participants.addAll(it)
        }
    }

    LaunchedEffect(slug) { reload() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.size) }
    }

    fun stopPlayer() {
        player?.runCatching { stop() }
        player?.release(); player = null; playingId = null
    }

    fun playVoice(m: ChatMessage) {
        val url = fileUrl(m.voiceUrl) ?: return
        if (playingId == m.id) { stopPlayer(); return }
        stopPlayer()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start(); playingId = m.id }
                setOnCompletionListener { stopPlayer() }
                setOnErrorListener { _, _, _ -> stopPlayer(); true }
                prepareAsync()
            }
        }
    }

    fun openFile(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            recorder?.runCatching { release() }
        }
    }

    val showSend = draft.trim().isNotEmpty() || replyTo != null
    val showMentions = Regex("@([^\\s@]*)$").containsMatchIn(draft) && participants.any { !it.username.isNullOrEmpty() }

    fun send() {
        val body = draft.trim()
        val reply = replyTo?.id
        draft = ""; replyTo = null
        if (body.isEmpty()) return
        scope.launch {
            runCatching { Repository.sendMessage(slug, body, reply) }.getOrNull()?.let { messages.add(it.toUi()) }
        }
    }

    fun sendAttachment(uri: Uri, kind: String) {
        val reply = replyTo?.id
        replyTo = null
        scope.launch {
            val part = Uploads.filePart(context, "attachment", uri) ?: return@launch
            val parts = mutableListOf(part, Uploads.textPart("kind", kind))
            reply?.let { parts.add(Uploads.textPart("reply_to", it.toString())) }
            runCatching { Repository.sendMessageAttachment(slug, parts) }.getOrNull()?.let { messages.add(it.toUi()) }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendAttachment(uri, "image")
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendAttachment(uri, "file")
    }

    @Suppress("DEPRECATION")
    fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    fun startRecording() {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = newRecorder()
        val ok = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (!ok) { rec.runCatching { release() }; return }
        recorder = rec; recFile = file; recSeconds = 0; recState = "recording"
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    fun onMicTap() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecorder(): Boolean {
        val rec = recorder ?: return false
        val ok = rec.runCatching { stop() }.isSuccess
        rec.runCatching { release() }
        recorder = null
        return ok
    }

    fun cancelRecording() {
        stopRecorder()
        recFile?.delete(); recFile = null
        recState = "idle"; recSeconds = 0
    }

    fun sendRecording() {
        val ok = stopRecorder()
        val file = recFile; val seconds = recSeconds
        recFile = null; recState = "idle"; recSeconds = 0
        if (!ok || file == null || !file.exists() || file.length() == 0L) { file?.delete(); return }
        val reply = replyTo?.id; replyTo = null
        scope.launch {
            val body = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("attachment", "voice.m4a", body)
            val parts = mutableListOf(part, Uploads.textPart("kind", "voice"), Uploads.textPart("duration", seconds.toString()))
            reply?.let { parts.add(Uploads.textPart("reply_to", it.toString())) }
            runCatching { Repository.sendMessageAttachment(slug, parts) }.getOrNull()?.let { messages.add(it.toUi()) }
            file.delete()
        }
    }

    LaunchedEffect(recState) {
        while (recState == "recording") { delay(1000); recSeconds += 1 }
    }

    fun react(msg: ChatMessage, emoji: String) {
        menuFor = null
        scope.launch {
            runCatching { Repository.reactMessage(slug, msg.id, emoji) }.getOrNull()?.let { upd ->
                val i = messages.indexOfFirst { it.id == msg.id }
                if (i >= 0) messages[i] = upd.toUi()
            }
        }
    }

    fun togglePin(msg: ChatMessage) {
        menuFor = null
        scope.launch {
            val p = runCatching { Repository.pinMessage(slug, msg.id) }.getOrNull()
            pinnedQuote = p
            for (i in messages.indices) {
                val m = messages[i]
                val shouldPin = p != null && p.id == m.id
                if (m.pinned != shouldPin) messages[i] = m.copy(pinned = shouldPin)
            }
        }
    }

    fun copyText(msg: ChatMessage) {
        menuFor = null
        val text = msg.text ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("message", text))
    }

    fun removeMessage(msg: ChatMessage) {
        menuFor = null
        scope.launch {
            runCatching { Repository.deleteMessage(slug, msg.id) }
            messages.removeAll { it.id == msg.id }
            if (pinnedQuote?.id == msg.id) pinnedQuote = null
        }
    }

    fun deleteSelected() {
        val ids = selected.toList()
        selectMode = false; selected.clear()
        scope.launch {
            for (id in ids) {
                runCatching { Repository.deleteMessage(slug, id) }
                messages.removeAll { it.id == id }
                if (pinnedQuote?.id == id) pinnedQuote = null
            }
        }
    }

    fun deleteViewerPhoto() {
        val m = viewerMsg
        viewerMsg = null
        if (m != null) removeMessage(m)
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
                            if (chatSubtitle.isNotEmpty()) Text(chatSubtitle, color = T.TextSecondary, fontSize = T.FsSm)
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
            pinnedQuote?.let { pin ->
                if (!selectMode) {
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
                            Text("Закреплённое сообщение", color = T.Text, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                            Text(pin.text ?: "", color = T.TextSecondary, fontSize = T.FsSm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AppIcon(AppIcons.Close, size = 18.dp, modifier = Modifier.clickable {
                            messages.find { it.id == pin.id }?.let { togglePin(it) }
                                ?: run { pinnedQuote = null }
                        })
                    }
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = T.ScreenX),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.clip(T.ShapePill).background(T.SurfaceMuted).padding(horizontal = 14.dp, vertical = 4.dp),
                            ) { Text("Сегодня", color = T.TextSecondary, fontSize = T.FsSm) }
                        }
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
                                    .clickable { m.userId?.let { onUser(it) } },
                            )
                        }
                        MessageBubble(
                            m,
                            modifier = Modifier.clickable {
                                when {
                                    selectMode -> if (selected.contains(m.id)) selected.remove(m.id) else selected.add(m.id)
                                    m.imageUrl != null -> viewerMsg = m
                                    else -> menuFor = m
                                }
                            },
                            playingVoiceId = playingId,
                            onPlayVoice = { playVoice(it) },
                            onOpenFile = { openFile(it) },
                        )
                    }
                }
            }

            // Composer / select actions / recording
            when {
                selectMode -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 16.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RoundBtn(AppIcons.Trash, danger = true) { deleteSelected() }
                    }
                }
                recState == "recording" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 12.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RoundBtn(AppIcons.Trash, danger = true) { cancelRecording() }
                        Row(
                            modifier = Modifier.weight(1f).height(48.dp).clip(T.ShapePill).background(T.SurfaceMuted).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFE23B3B)))
                            val mm = recSeconds / 60
                            val ss = (recSeconds % 60).toString().padStart(2, '0')
                            Text("$mm:$ss", color = T.Text, fontSize = T.FsBase)
                            Text("Запись…", color = T.TextSecondary, fontSize = T.FsBase)
                        }
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(T.GradientAccent).clickable { sendRecording() },
                            contentAlignment = Alignment.Center,
                        ) { AppIcon(AppIcons.Forward, size = 22.dp, tint = Color.White) }
                    }
                }
                else -> {
                    Composer(
                        draft = draft,
                        onDraft = { draft = it },
                        showSend = showSend,
                        attachOpen = attachOpen,
                        onToggleAttach = { attachOpen = !attachOpen },
                        onPickImage = { attachOpen = false; imagePicker.launch("image/*") },
                        onPickFile = { attachOpen = false; filePicker.launch("*/*") },
                        showMentions = showMentions,
                        mentions = participants.filter { !it.username.isNullOrEmpty() },
                        onPickMention = { handle -> draft = draft.replace(Regex("@([^\\s@]*)$"), "$handle ") },
                        replyTo = replyTo,
                        onCancelReply = { replyTo = null },
                        onSend = { send() },
                        onMic = { onMicTap() },
                    )
                }
            }
        }

        // Context menu
        menuFor?.let { msg ->
            MessageMenu(
                message = msg,
                onClose = { menuFor = null },
                onReact = { react(msg, it) },
                onReply = { replyTo = msg; menuFor = null },
                onCopy = { copyText(msg) },
                onPin = { togglePin(msg) },
                onDelete = { removeMessage(msg) },
                onSelect = { selectMode = true; selected.add(msg.id); menuFor = null },
            )
        }

        // Photo viewer
        viewerMsg?.let { m ->
            PhotoViewer(
                imageUrl = fileUrl(m.imageUrl),
                author = if (m.mine) "Вы" else (m.author ?: ""),
                time = m.time ?: "",
                onClose = { viewerMsg = null },
                onDelete = { deleteViewerPhoto() },
            )
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
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    showMentions: Boolean,
    mentions: List<ParticipantDto>,
    onPickMention: (String) -> Unit,
    replyTo: ChatMessage?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 12.dp)) {
        if (attachOpen) {
            Column(
                modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(T.Surface).padding(6.dp),
            ) {
                AttachItem(AppIcons.Doc, "Изображение") { onPickImage() }
                AttachItem(AppIcons.Doc, "Файл") { onPickFile() }
            }
        }
        if (showMentions && mentions.isNotEmpty()) {
            Column(modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(T.Surface).padding(4.dp)) {
                mentions.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPickMention(p.username ?: "") }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF8A8A8A)))
                        Text(p.name, color = T.Text, fontSize = T.FsBase)
                        Text(p.username ?: "", color = T.TextSecondary, fontSize = T.FsSm)
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
                    Text(replyTo.text ?: "Вложение", color = T.TextSecondary, fontSize = T.FsSm, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                modifier = Modifier.size(44.dp).clip(CircleShape).then(if (showSend) Modifier.background(T.GradientAccent) else Modifier.background(T.SurfaceMuted)).clickable { if (showSend) onSend() else onMic() },
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
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
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
                REACTIONS.forEach { emoji ->
                    Text(emoji, fontSize = T.FsMd, modifier = Modifier.clickable { onReact(emoji) })
                }
            }
            MessageBubble(message)
            Column(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(T.Surface).width(240.dp),
            ) {
                MenuItem(AppIcons.Reply, "Ответить", onClick = onReply)
                if (!message.text.isNullOrEmpty()) MenuItem(AppIcons.Copy, "Скопировать", onClick = onCopy)
                MenuItem(AppIcons.Pin, if (message.pinned) "Открепить" else "Закрепить", onClick = onPin)
                MenuItem(AppIcons.Trash, "Удалить", danger = true, onClick = onDelete)
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
private fun PhotoViewer(imageUrl: String?, author: String, time: String, onClose: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xF2000000))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = T.ScreenX, end = T.ScreenX, top = insetTop() + 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(AppIcons.Back, size = 24.dp, tint = Color.White, modifier = Modifier.clickable { onClose() })
            Column(modifier = Modifier.weight(1f)) {
                Text(author, color = Color.White, fontSize = T.FsBase, fontWeight = FontWeight.Bold)
                if (time.isNotEmpty()) Text("Сегодня в $time", color = T.TextSecondary, fontSize = T.FsSm)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (imageUrl != null) {
                AsyncImageViewer(imageUrl)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = T.ScreenX).padding(bottom = insetBottom() + 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundBtn(AppIcons.Trash, danger = true) { onDelete() }
        }
    }
}

@Composable
private fun AsyncImageViewer(url: String) {
    coil.compose.AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
    )
}
