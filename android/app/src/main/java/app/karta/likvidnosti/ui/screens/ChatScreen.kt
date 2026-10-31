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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.karta.likvidnosti.BuildConfig
import app.karta.likvidnosti.data.ChatDates
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.MessageDto
import app.karta.likvidnosti.data.api.MessageQuoteDto
import app.karta.likvidnosti.data.api.ParticipantDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AvatarCircle
import app.karta.likvidnosti.ui.components.figmaFade
import app.karta.likvidnosti.ui.components.figmaTween
import app.karta.likvidnosti.ui.components.chatSpring
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.ChatMessage
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.GlassBackButton
import app.karta.likvidnosti.ui.components.GlassCircleButton
import app.karta.likvidnosti.ui.components.MessageBubble
import app.karta.likvidnosti.ui.components.Reaction
import app.karta.likvidnosti.ui.components.RemoteImage
import coil.compose.AsyncImage
import app.karta.likvidnosti.ui.components.ReplyQuote
import app.karta.likvidnosti.ui.components.ReportCover
import app.karta.likvidnosti.ui.components.SkeletonBox
import app.karta.likvidnosti.ui.components.backdropBlur
import app.karta.likvidnosti.ui.components.fillShape
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.screenBackground
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.IconDef
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import app.karta.likvidnosti.ui.theme.statusBarTop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.UUID
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.derivedStateOf
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.ChatOutbox

private val REACTIONS = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "👏")
private val Secondary = Color.White.copy(alpha = 0.6f)
private val ChatSpring = figmaTween<Float>()

private fun MessageDto.toUi() = ChatMessage(
    id = id,
    mine = mine,
    userId = userId,
    author = author,
    role = role,
    avatarUrl = avatarUrl,
    text = text,
    time = ChatDates.time(createdAt, time),
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
    imageUrls = if (images.isNotEmpty()) images.map { it.url } else listOfNotNull(image?.url),
    replyAuthor = reply?.author,
    replyText = reply?.text,
    reactions = reactions.map { Reaction(it.emoji, it.count, it.mine) },
    pinned = pinned,
    createdAt = createdAt,
    event = event,
    isPrivate = isPrivate,
    privateLabel = privateLabel,
    canDelete = canDelete,
    clientId = clientId,
)

/**
 * Временный пузырь сразу в окончательном виде: своя строка ленты (ключ не сменится
 * с ответом сервера) и время отправки — как в Telegram, без дорисовки после ответа.
 */
private fun ChatMessage.asOutgoing(): ChatMessage {
    val now = OffsetDateTime.now().toString()
    return copy(
        localKey = localKey ?: "l$id",
        createdAt = createdAt ?: now,
        time = time ?: ChatDates.time(now),
    )
}

/**
 * Сообщение сервера встаёт на место прежней версии той же строки: ключ ленты прежний,
 * а уже показанные локальные фото остаются (не перезагружаются и не мигают).
 */
private fun ChatMessage.adopting(old: ChatMessage): ChatMessage = copy(
    localKey = old.localKey,
    localImageUris = if (old.localImageUris.isNotEmpty() && (imageUrl != null || imageUrls.isNotEmpty())) {
        old.localImageUris
    } else {
        emptyList()
    },
)

/** Своя реакция: поставить или снять (сервер делает так же). */
private fun List<Reaction>.toggled(emoji: String): List<Reaction> {
    val existing = firstOrNull { it.emoji == emoji }
    return when {
        existing == null -> this + Reaction(emoji, 1, true)
        existing.mine -> mapNotNull {
            if (it.emoji != emoji) it else if (it.count > 1) it.copy(count = it.count - 1, mine = false) else null
        }
        else -> map { if (it.emoji == emoji) it.copy(count = it.count + 1, mine = true) else it }
    }
}

/** Запрос «открыть поиск» из инфо чата: экран чата открывает поиск, когда к нему возвращаются. */
object ChatSearchBus {
    var request by mutableStateOf<String?>(null)
}

private fun sameAuthor(a: ChatMessage, b: ChatMessage): Boolean {
    if (a.isSystem || b.isSystem) return false
    if (a.mine != b.mine) return false
    if (a.mine) return true
    return a.userId != null && a.userId == b.userId
}

private fun replyPreview(m: ChatMessage): String = when {
    m.imageUrl != null -> "Фото"
    m.voiceUrl != null -> "Голосовое сообщение"
    m.fileName != null || m.fileUrl != null -> m.fileName ?: "Файл"
    else -> "Сообщение"
}

// «@» в начале слова у курсора (в конце текста) — открывает список упоминаний.
private val MentionRegex = Regex("(^|\\s)@[^\\s@]*$")
// «/текст/» — личное сообщение автору отчёта.
/** Вибрация записи голосового: системные паттерны, где они есть (Android 11+). */
private enum class VoiceHaptic { Start, Tick, Lock, Cancel, Send, Toggle }

private fun View.voiceHaptic(kind: VoiceHaptic) {
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val constant = when (kind) {
        VoiceHaptic.Start -> HapticFeedbackConstants.LONG_PRESS
        VoiceHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        VoiceHaptic.Lock -> if (modern) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        VoiceHaptic.Cancel -> if (modern) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        VoiceHaptic.Send -> if (modern) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        VoiceHaptic.Toggle -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
}

private val AuthorMessageRegex = Regex("^/(.*\\S.*)/$", RegexOption.DOT_MATCHES_ALL)

private fun searchDate(m: ChatMessage): String {
    val date = m.createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return m.time ?: ""
    val local = date.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    if (local == today) return m.time ?: ""
    val pattern = if (local.year == today.year) "d MMM" else "dd.MM.yy"
    return local.format(DateTimeFormatter.ofPattern(pattern, Locale("ru")))
}

/**
 * «Чат» (Figma 337:27172 и состояния 337:27570 … 337:36316) — порт iOS
 * `Screens/ChatView.swift`: фон с узором из треугольников, сверху на y=78 —
 * стеклянные «назад», пилюля с названием и числом участников, аватар 42;
 * закреп — стеклянная плашка под шапкой; снизу на 45 от края — «+», поле ввода
 * и микрофон (при наборе — зелёная «отправить» внутри поля). Запись голосового:
 * удержание микрофона, влево — отмена, вверх — замок; в замке — пауза и
 * отправка. Долгое нажатие — реакции и меню действий на размытом фоне; режим
 * выбора — «Выбрано N / Отмена», удалить и переслать.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    slug: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onUser: (Long) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var chatTitle by remember { mutableStateOf("Чат") }
    var chatSubtitle by remember { mutableStateOf("") }
    var chatAvatar by remember { mutableStateOf<String?>(null) }
    var chatType by remember { mutableStateOf<String?>(null) }
    // Закрепы по порядку закрепления; в шапке — текущий (сначала самый новый).
    val pins = remember { mutableStateListOf<MessageQuoteDto>() }
    var pinIndex by remember { mutableIntStateOf(0) }
    var canPin by remember { mutableStateOf(false) }
    var authorMessages by remember { mutableStateOf(false) }
    var unpinConfirm by remember { mutableStateOf<MessageQuoteDto?>(null) }
    val mentionCandidates = remember { mutableStateListOf<ParticipantDto>() }
    var mentionJob by remember { mutableStateOf<Job?>(null) }
    // Подсветка сообщения после перехода к нему (закреп, поиск).
    var flashId by remember { mutableStateOf<Long?>(null) }
    // Поиск по чату (как в Телеграме).
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchIndex by remember { mutableIntStateOf(0) }
    var searchListOpen by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // Сообщения первой загрузки появляются без анимации «всплытия».
    val initialIds = remember { HashSet<String>() }

    var draft by remember { mutableStateOf(TextFieldValue("")) }
    // Фото, выбранные через системный Photo Picker, ждут «отправить».
    val pendingImages = remember { mutableStateListOf<Uri>() }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var deleteMenu by remember { mutableStateOf(false) }
    var selectDeleteMenu by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var attachOpen by remember { mutableStateOf(false) }
    var viewerMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Запись голосового
    var recording by remember { mutableStateOf(false) }
    var recPaused by remember { mutableStateOf(false) }
    var recLocked by remember { mutableStateOf(false) }
    var micPressed by remember { mutableStateOf(false) }
    var recordDrag by remember { mutableStateOf(Offset.Zero) }
    var recSeconds by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<File?>(null) }

    // Воспроизведение голосового
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var voiceProgress by remember { mutableStateOf(0f) }

    var composerHeight by remember { mutableIntStateOf(0) }

    val isDirect = slug == "admin" || slug.startsWith("dm-") || chatType == "direct"
    val visible: List<ChatMessage> = messages
    val showSend = draft.text.trim().isNotEmpty() || pendingImages.isNotEmpty()
    val mentionQuery = MentionRegex.find(draft.text)?.value?.trim()?.drop(1)
    val mentionMatches: List<ParticipantDto> = if (mentionQuery == null) emptyList() else {
        val q = mentionQuery.lowercase()
        mentionCandidates.filter { p ->
            q.isEmpty() ||
                (p.username?.lowercase()?.contains(q) == true) ||
                p.name.lowercase().contains(q)
        }.take(5)
    }
    val draftIsAuthorMessage = authorMessages && AuthorMessageRegex.matches(draft.text.trim())
    // Кто увидит личное сообщение «/текст/» или ответ в личной ветке.
    val privateHint = when {
        replyTo?.isPrivate == true -> "Ответ увидят только участники личной переписки"
        !draftIsAuthorMessage -> null
        chatType == "report" -> "Сообщение увидят только автор отчета и администраторы"
        else -> "Сообщение увидят только администраторы"
    }
    val currentPin = pins.getOrNull(pinIndex.coerceIn(0, (pins.size - 1).coerceAtLeast(0)))
    // Результаты поиска по всем сообщениям чата: от старых к новым.
    val searchResults: List<Long> = searchQuery.trim().let { q ->
        if (q.isEmpty()) emptyList() else visible.filter { m ->
            !m.isSystem && listOfNotNull(m.text, m.fileName).any { it.contains(q, ignoreCase = true) }
        }.map { it.id }
    }
    // Текущий результат: счёт от самого нового (1 из N), стрелка вверх — к более старым.
    val currentSearchId = searchResults.takeIf { it.isNotEmpty() }?.let { it[it.size - 1 - searchIndex.coerceAtMost(it.size - 1)] }

    // MARK: - Загрузка

    // Неотправленные сообщения: id временного пузыря → запрос, которым его можно отправить ещё раз.
    val retryRequests = remember { HashMap<Long, suspend () -> MessageDto?>() }

    suspend fun reloadMessages() {
        runCatching { Repository.messages(slug) }.getOrNull()?.let { resp ->
            resp.chat?.let {
                chatTitle = it.title
                chatSubtitle = it.subtitle ?: ""
                chatAvatar = fileUrl(it.avatarUrl)
                chatType = it.type
            }
            resp.chat?.let {
                canPin = it.canPin
                authorMessages = it.authorMessages
            }
            val newPins = resp.pins ?: listOfNotNull(resp.pinned)
            pins.clear(); pins.addAll(newPins)
            pinIndex = pinIndex.coerceIn(0, (pins.size - 1).coerceAtLeast(0))
            // Строки ленты сохраняют ключи: сообщение, уже стоящее на экране, обновляется
            // на месте, а не пересоздаётся. Отправляемое, которое сервер уже принял
            // (потерялся только ответ), сменяется серверным без дубля.
            val byId = messages.associateBy { it.id }
            val outgoing = messages.filter { it.uploading || it.failed }
            val outgoingByCid = outgoing.filter { it.clientId != null }.associateBy { it.clientId }
            val server = resp.data.map { dto ->
                val ui = dto.toUi()
                val old = byId[ui.id] ?: ui.clientId?.let { outgoingByCid[it] }
                if (old != null) ui.adopting(old) else ui
            }
            val serverCids = server.mapNotNull { it.clientId }.toHashSet()
            val pending = outgoing.filter { m ->
                val landed = m.clientId != null && m.clientId in serverCids
                if (landed) {
                    retryRequests.remove(m.id)
                    ChatOutbox.remove(slug, m.id)
                }
                !landed
            }
            messages.clear()
            messages.addAll(server)
            messages.addAll(pending)
        }
    }

    /** Запрос отправки альбома из jpeg-файлов очереди, с кольцом загрузки на превью. */
    fun photoRequest(localId: Long, clientId: String, files: List<File>, caption: String, reply: Long?): suspend () -> MessageDto? = {
        val total = files.sumOf { it.length() }
        val counter = java.util.concurrent.atomic.AtomicLong(0)
        val field = if (files.size == 1) "attachment" else "attachments[]"
        val parts = files.map { f ->
            val body = Uploads.ProgressBody(f.asRequestBody("image/jpeg".toMediaTypeOrNull()), counter, total) { p ->
                scope.launch {
                    val i = messages.indexOfFirst { it.id == localId }
                    if (i >= 0 && messages[i].uploading) messages[i] = messages[i].copy(uploadProgress = p)
                }
            }
            MultipartBody.Part.createFormData(field, f.name, body)
        }.toMutableList()
        parts.add(Uploads.textPart("kind", "image"))
        parts.add(Uploads.textPart("client_id", clientId))
        if (caption.isNotEmpty()) parts.add(Uploads.textPart("body", caption))
        reply?.let { parts.add(Uploads.textPart("reply_to", it.toString())) }
        Repository.sendMessageAttachment(slug, parts)
    }

    /**
     * Неотправленное с прошлого раза (вышли из чата или закрыли приложение) —
     * снова в ленте со статусом «не отправлено» и «Повторить».
     */
    fun restoreOutbox() {
        val serverKeys = messages.mapNotNull { if (it.id > 0) it.clientId else null }.toSet()
        ChatOutbox.entries(slug).forEach { e ->
            if (messages.any { it.id == e.localId }) return@forEach
            // Сообщение на самом деле дошло (потерялся только ответ) — дубль не показываем.
            if (e.clientId in serverKeys) { ChatOutbox.remove(slug, e.localId); return@forEach }
            val files = e.photos.map { File(it) }
            if (files.any { !it.exists() }) { ChatOutbox.remove(slug, e.localId); return@forEach }
            messages.add(
                ChatMessage(
                    id = e.localId,
                    mine = true,
                    author = Session.user?.name,
                    text = e.text,
                    localImageUris = files.map { Uri.fromFile(it) },
                    failed = true,
                    replyAuthor = e.replyAuthor,
                    replyText = e.replyText,
                    isPrivate = e.isPrivate,
                    clientId = e.clientId,
                ).asOutgoing(),
            )
            retryRequests[e.localId] = if (files.isEmpty()) {
                { Repository.sendMessage(slug, e.text.orEmpty(), e.replyTo, e.clientId) }
            } else {
                photoRequest(e.localId, e.clientId, files, e.text.orEmpty(), e.replyTo)
            }
        }
    }

    /**
     * Новые сообщения с сервера, пока чат открыт: добавляются снизу, свои
     * (ушедшие с этого или другого устройства) заменяют «часики».
     */
    suspend fun pollNew() {
        if (!loaded || selectMode) return
        val lastId = messages.lastOrNull { it.id > 0 }?.id ?: return
        val resp = runCatching { Repository.messages(slug, after = lastId) }.getOrNull() ?: return
        val newPins = resp.pins ?: listOfNotNull(resp.pinned)
        if (newPins.map { it.id } != pins.map { it.id }) {
            pins.clear(); pins.addAll(newPins)
            pinIndex = pinIndex.coerceIn(0, (pins.size - 1).coerceAtLeast(0))
        }
        val known = messages.map { it.id }.toHashSet()
        val fresh = resp.data.filter { it.id > lastId && it.id !in known }.map { it.toUi() }
        if (fresh.isEmpty()) return
        // Своё сообщение, пришедшее раньше ответа на отправку, занимает строку своих
        // часиков (тот же ключ ленты) — без исчезновения и повторного появления.
        val ordered = fresh.map { m ->
            val cid = m.clientId ?: return@map m
            val i = messages.indexOfFirst { it.id < 0 && it.clientId == cid }
            if (i < 0) return@map m
            val old = messages[i]
            retryRequests.remove(old.id)
            ChatOutbox.remove(slug, old.id)
            messages.removeAt(i)
            m.adopting(old)
        }
        val local = messages.filter { it.id < 0 }
        messages.removeAll { it.id < 0 }
        messages.addAll(ordered)
        messages.addAll(local)
    }

    LaunchedEffect(slug) {
        reloadMessages()
        restoreOutbox()
        pinIndex = (pins.size - 1).coerceAtLeast(0)
        initialIds.addAll(messages.map { it.key })
        loaded = true
    }

    // Пока чат на экране и приложение активно — тихо догружаем новые сообщения.
    val chatLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        chatLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            pollNew()
            while (true) {
                delay(8_000)
                pollNew()
            }
        }
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val keyboardShown = imeBottom > 0.dp
    // Отступ плавно идёт за клавиатурой и не прыгает с 12 на 45 в конце её скрытия.
    val bottomPad = maxOf(imeBottom + 12.dp, designBottom(45.dp))

    // Палец тянет ленту вниз (к старым сообщениям) — клавиатура уходит, как в iOS.
    val keyboardOpen by rememberUpdatedState(keyboardShown)
    val dragDismissKeyboard = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 4f && keyboardOpen) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
                return Offset.Zero
            }
        }
    }

    // К последнему сообщению — при новых сообщениях и когда клавиатура появилась.
    // Когда она убрана (долгое нажатие, выбор), лента остаётся на месте: иначе
    // зажатое сообщение уезжает, и после меню виден уже другой участок чата.
    var keyboardWas by remember { mutableStateOf(false) }
    // Лента внизу (видно последнее сообщение). Если пользователь читает старые,
    // новые сообщения не утаскивают его вниз — появляется плашка «Новые сообщения».
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    var lastSize by remember { mutableIntStateOf(0) }
    var newBelow by remember { mutableIntStateOf(0) }
    LaunchedEffect(nearBottom) { if (nearBottom) newBelow = 0 }
    LaunchedEffect(visible.size, loaded, keyboardShown) {
        val keyboardHidden = keyboardWas && !keyboardShown
        keyboardWas = keyboardShown
        val added = visible.size - lastSize
        val hadList = lastSize > 0
        if (loaded) lastSize = visible.size
        if (loaded && visible.isNotEmpty() && !searchMode && !keyboardHidden) {
            val readingOld = hadList && added > 0 && !nearBottom && visible.lastOrNull()?.mine != true
            if (readingOld) {
                newBelow += added
            } else {
                runCatching { listState.animateScrollToItem(visible.size - 1) }
            }
        }
    }

    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        delay(1800)
        if (toast == t) toast = null
    }

    fun showToast(text: String) { toast = text }

    // MARK: - Воспроизведение

    fun stopPlayer() {
        player?.runCatching { stop() }
        player?.release(); player = null; playingId = null; voiceProgress = 0f
    }

    fun playVoice(m: ChatMessage) {
        // Своё голосовое, пока не ушло на сервер, играет из локального файла.
        val url = (if (m.id < 0) m.voiceUrl else fileUrl(m.voiceUrl)) ?: return
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

    // Пока голосовое играет — заполняем волну по позиции плеера.
    LaunchedEffect(playingId) {
        voiceProgress = 0f
        val p = player
        if (playingId != null && p != null) {
            while (playingId != null) {
                val dur = runCatching { p.duration }.getOrDefault(0)
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                if (dur > 0) voiceProgress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                delay(50)
            }
        }
    }

    fun openUrl(url: String?) {
        val u = url ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }

    // MARK: - Отправка

    var failedMenuFor by remember { mutableStateOf<Long?>(null) }

    /** Отправка с часиками: пузырь сразу в ленте, после ответа сервера — настоящий, при ошибке — «!». */
    fun runSend(tempId: Long, request: suspend () -> MessageDto?) {
        retryRequests[tempId] = request
        scope.launch {
            runCatching { request() }
                .onSuccess { sent ->
                    retryRequests.remove(tempId)
                    ChatOutbox.remove(slug, tempId)
                    val idx = messages.indexOfFirst { it.id == tempId }
                    val ui = sent?.toUi()
                    when {
                        ui == null || messages.any { it.id == ui.id } -> if (idx >= 0) messages.removeAt(idx)
                        // Та же строка ленты: меняются только часики на кружок, без перерисовки.
                        idx >= 0 -> messages[idx] = ui.adopting(messages[idx])
                        else -> messages.add(ui)
                    }
                }
                .onFailure { e ->
                    val idx = messages.indexOfFirst { it.id == tempId }
                    if (idx >= 0) messages[idx] = messages[idx].copy(uploading = false, failed = true, uploadProgress = null)
                    showToast(e.message?.takeIf { it.isNotBlank() && it.length < 120 } ?: "Сообщение не отправлено")
                }
        }
    }

    fun sendOptimistic(temp: ChatMessage, outbox: ChatOutbox.Entry? = null, request: suspend () -> MessageDto?) {
        // Текст и фото — в очередь на диске: не ушло — не потеряется при выходе из чата.
        outbox?.let { ChatOutbox.add(slug, it) }
        messages.add(temp.copy(mine = true, author = Session.user?.name, uploading = true, failed = false).asOutgoing())
        runSend(temp.id, request)
    }

    fun retryFailed(id: Long) {
        failedMenuFor = null
        val request = retryRequests[id] ?: return
        val idx = messages.indexOfFirst { it.id == id }
        if (idx < 0) return
        messages[idx] = messages[idx].copy(
            uploading = true,
            failed = false,
            uploadProgress = if (messages[idx].localImageUris.isEmpty()) null else 0f,
        )
        runSend(id, request)
    }

    fun discardFailed(id: Long) {
        failedMenuFor = null
        retryRequests.remove(id)
        ChatOutbox.remove(slug, id)
        messages.removeAll { it.id == id }
    }

    /** Файл сразу в ленте с часиками; не ушёл — «!» и «Повторить» (тем же ключом, без дубля). */
    fun sendAttachment(uri: Uri, kind: String) {
        val reply = replyTo?.id
        // Текст из поля ввода становится подписью к файлу (голосовое без подписи).
        val caption = if (kind == "voice") "" else draft.text.trim()
        replyTo = null
        attachOpen = false
        if (caption.isNotEmpty()) draft = TextFieldValue("")
        val clientId = UUID.randomUUID().toString()
        val name = Uploads.displayName(context, uri)
        val temp = ChatMessage(
            id = -System.nanoTime(),
            mine = true,
            text = caption.ifEmpty { null },
            fileName = name,
            fileExt = name.substringAfterLast('.', ""),
            clientId = clientId,
        )
        sendOptimistic(temp) {
            val part = Uploads.filePart(context, "attachment", uri)
                ?: throw ApiException("Файл не прочитался, попробуйте ещё раз", 0)
            if (part.body.contentLength() > 100L * 1024 * 1024) throw ApiException("Файл больше 100 МБ, выберите файл поменьше", 0)
            val parts = mutableListOf(part, Uploads.textPart("kind", kind), Uploads.textPart("client_id", clientId))
            if (caption.isNotEmpty()) parts.add(Uploads.textPart("body", caption))
            reply?.let { parts.add(Uploads.textPart("reply_to", it.toString())) }
            Repository.sendMessageAttachment(slug, parts)
        }
    }

    /**
     * Фото из поля ввода: сообщение сразу в ленте с превью и кольцом загрузки. Фото
     * перекодируются в JPEG и ложатся в очередь на диске: не ушло — остаётся с «!»
     * (и не пропадёт при выходе из чата), «Повторить» шлёт его один раз.
     */
    fun sendPendingPhotos() {
        val uris = pendingImages.toList()
        if (uris.isEmpty()) return
        val caption = draft.text.trim()
        val reply = replyTo?.id
        pendingImages.clear()
        draft = TextFieldValue("")
        replyTo = null
        attachOpen = false
        val tempId = -System.nanoTime()
        val clientId = UUID.randomUUID().toString()
        messages.add(
            ChatMessage(
                id = tempId,
                mine = true,
                author = Session.user?.name,
                text = caption.ifEmpty { null },
                localImageUris = uris,
                uploading = true,
                uploadProgress = 0f,
                clientId = clientId,
            ).asOutgoing(),
        )
        fun revert(error: String) {
            messages.removeAll { it.id == tempId }
            pendingImages.addAll(0, uris)
            if (caption.isNotEmpty()) draft = TextFieldValue(caption)
            showToast(error)
        }
        scope.launch {
            val dir = ChatOutbox.photoDir()
            val files = withContext(Dispatchers.IO) {
                if (dir == null) emptyList() else uris.mapIndexedNotNull { i, uri ->
                    Uploads.jpegBytes(context, uri)?.let { bytes ->
                        File(dir, "photo-${-tempId}-$i.jpg").apply { writeBytes(bytes) }
                    }
                }
            }
            if (files.size != uris.size) {
                files.forEach { it.delete() }
                revert(if (uris.size == 1) "Файл не прочитался, попробуйте ещё раз" else "Одно из фото не прочиталось, попробуйте ещё раз")
                return@launch
            }
            if (files.sumOf { it.length() } > 100L * 1024 * 1024) {
                files.forEach { it.delete() }
                revert("Фото больше 100 МБ, выберите фото поменьше")
                return@launch
            }
            ChatOutbox.add(
                slug,
                ChatOutbox.Entry(
                    localId = tempId, clientId = clientId, text = caption.ifEmpty { null }, replyTo = reply,
                    photos = files.map { it.absolutePath },
                ),
            )
            runSend(tempId, photoRequest(tempId, clientId, files, caption, reply))
        }
    }

    fun send() {
        if (pendingImages.isNotEmpty()) {
            sendPendingPhotos()
            return
        }
        val body = draft.text.trim()
        if (body.isEmpty()) return
        val reply = replyTo
        draft = TextFieldValue("")
        replyTo = null
        val clientId = UUID.randomUUID().toString()
        val temp = ChatMessage(
            id = -System.nanoTime(),
            mine = true,
            text = body,
            replyAuthor = reply?.let { if (it.mine) "Вы" else it.author },
            replyText = reply?.let { it.text ?: replyPreview(it) },
            // Ответ в личной ветке и «/текст/» уходят лично — пузырь не должен выглядеть общим.
            isPrivate = reply?.isPrivate == true || draftIsAuthorMessage,
            clientId = clientId,
        )
        val entry = ChatOutbox.Entry(
            localId = temp.id, clientId = clientId, text = body, replyTo = reply?.id,
            replyAuthor = temp.replyAuthor, replyText = temp.replyText, isPrivate = temp.isPrivate,
        )
        sendOptimistic(temp, entry) { Repository.sendMessage(slug, body, reply?.id, clientId) }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) pendingImages.addAll(uris)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendAttachment(uri, "file")
    }

    // MARK: - Запись голосового

    @Suppress("DEPRECATION")
    fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    fun startRecording() {
        if (recording) return
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
        recorder = rec; recFile = file; recSeconds = 0
        recPaused = false; recLocked = false
        recording = true
        view.voiceHaptic(VoiceHaptic.Start)
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Разрешение выдано — запись начнётся при следующем удержании микрофона.
        if (granted) showToast("Удерживайте микрофон для записи")
    }

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecorder(): Boolean {
        val rec = recorder ?: return false
        if (recPaused) rec.runCatching { resume() }
        val ok = rec.runCatching { stop() }.isSuccess
        rec.runCatching { release() }
        recorder = null
        return ok
    }

    fun cancelVoice() {
        view.voiceHaptic(VoiceHaptic.Cancel)
        stopRecorder()
        recFile?.delete(); recFile = null
        recording = false; recPaused = false; recLocked = false
        recSeconds = 0; recordDrag = Offset.Zero
    }

    fun stopAndSendVoice() {
        recLocked = false
        val ok = stopRecorder()
        val file = recFile
        val seconds = max(recSeconds, 1)
        recFile = null; recording = false; recPaused = false; recSeconds = 0
        if (!ok || file == null || !file.exists() || file.length() == 0L) { file?.delete(); return }
        view.voiceHaptic(VoiceHaptic.Send)
        val reply = replyTo; replyTo = null
        val voiceClientId = UUID.randomUUID().toString()
        sendOptimistic(
            ChatMessage(
                id = -System.nanoTime(),
                mine = true,
                clientId = voiceClientId,
                voiceUrl = file.absolutePath,
                voiceDuration = "%d:%02d".format(seconds / 60, seconds % 60),
                replyAuthor = reply?.let { if (it.mine) "Вы" else it.author },
                replyText = reply?.let { it.text ?: replyPreview(it) },
                isPrivate = reply?.isPrivate == true,
            ),
        ) {
            // Запрос собирается заново при каждой попытке: тело файла читается один раз.
            val body = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("attachment", "voice.m4a", body)
            val parts = mutableListOf(
                part,
                Uploads.textPart("kind", "voice"),
                Uploads.textPart("duration", seconds.toString()),
                Uploads.textPart("client_id", voiceClientId),
            )
            reply?.id?.let { parts.add(Uploads.textPart("reply_to", it.toString())) }
            Repository.sendMessageAttachment(slug, parts).also { file.delete() }
        }
    }

    fun pauseRecording() {
        if (!recording || recPaused) return
        if (recorder?.runCatching { pause() }?.isSuccess == true) { recPaused = true; view.voiceHaptic(VoiceHaptic.Toggle) }
    }

    fun resumeRecording() {
        if (!recording || !recPaused) return
        if (recorder?.runCatching { resume() }?.isSuccess == true) { recPaused = false; view.voiceHaptic(VoiceHaptic.Toggle) }
    }

    LaunchedEffect(recording, recPaused) {
        while (recording && !recPaused) { delay(1000); recSeconds += 1 }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            recorder?.runCatching { release() }
            recFile?.delete()
        }
    }

    // Удержание микрофона: влево — отмена, вверх — замок, отпустить — отправить.
    val onMicDown by rememberUpdatedState<() -> Unit>({
        micPressed = true
        focusManager.clearFocus()
        startVoice()
    })
    val onMicDrag by rememberUpdatedState<(Offset, Float, Float) -> Unit>({ d, cancelPx, lockPx ->
        if (recording && !recLocked) {
            // Половина пути до отмены или замка — лёгкий щелчок-подсказка.
            val prev = recordDrag
            if (d.x < -cancelPx / 2 && prev.x >= -cancelPx / 2) view.voiceHaptic(VoiceHaptic.Tick)
            if (d.y < -lockPx / 2 && prev.y >= -lockPx / 2) view.voiceHaptic(VoiceHaptic.Tick)
            recordDrag = d
            if (d.x < -cancelPx) {
                cancelVoice()
            } else if (d.y < -lockPx) {
                view.voiceHaptic(VoiceHaptic.Lock)
                recLocked = true
                recordDrag = Offset.Zero
            }
        }
    })
    val onMicUp by rememberUpdatedState<() -> Unit>({
        micPressed = false
        recordDrag = Offset.Zero
        if (recording && !recLocked) stopAndSendVoice()
    })

    // MARK: - Действия с сообщениями

    fun closeMenu() { menuFor = null; deleteMenu = false }

    // Реакции: номер последнего нажатия по сообщению — ответ сервера на старое нажатие
    // не перетирает то, что уже показано после нового.
    val reactSeq = remember { HashMap<Long, Int>() }

    /**
     * Реакция — сразу на экране, как в Telegram; сервер подтверждает в фоне.
     * Не удалось — нажатие откатывается (повторное переключение той же реакции) и тост.
     */
    fun react(id: Long, emoji: String) {
        closeMenu()
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) return
        messages[i] = messages[i].copy(reactions = messages[i].reactions.toggled(emoji))
        val seq = (reactSeq[id] ?: 0) + 1
        reactSeq[id] = seq
        scope.launch {
            runCatching { Repository.reactMessage(slug, id, emoji) }
                .onSuccess { dto ->
                    if (dto == null || reactSeq[id] != seq) return@onSuccess
                    val j = messages.indexOfFirst { it.id == id }
                    val fresh = dto.toUi().reactions
                    if (j >= 0 && fresh != messages[j].reactions) messages[j] = messages[j].copy(reactions = fresh)
                }
                .onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    val j = messages.indexOfFirst { it.id == id }
                    if (j >= 0) messages[j] = messages[j].copy(reactions = messages[j].reactions.toggled(emoji))
                    showToast("Не удалось поставить реакцию")
                }
        }
    }

    fun clampPinIndex() {
        pinIndex = pinIndex.coerceIn(0, (pins.size - 1).coerceAtLeast(0))
    }

    fun applyPins(list: List<MessageQuoteDto>, focusNewest: Boolean) {
        pins.clear(); pins.addAll(list)
        if (focusNewest) pinIndex = (pins.size - 1).coerceAtLeast(0) else clampPinIndex()
        val ids = list.map { it.id }.toSet()
        for (idx in messages.indices) {
            val m = messages[idx]
            val p = m.id in ids
            if (m.pinned != p) messages[idx] = m.copy(pinned = p)
        }
    }

    /** Закрепить (сразу) или открепить (после подтверждения). */
    fun togglePin(id: Long) {
        closeMenu()
        if (!canPin) return
        val m = messages.firstOrNull { it.id == id }
        if (m?.pinned == true || pins.any { it.id == id }) {
            unpinConfirm = pins.firstOrNull { it.id == id } ?: MessageQuoteDto(id, m?.author, m?.text)
            return
        }
        scope.launch {
            runCatching { Repository.pinMessage(slug, id) }
                .onSuccess { applyPins(it, focusNewest = true); showToast("Сообщение закреплено") }
                .onFailure { showToast("Не удалось закрепить") }
        }
    }

    fun unpin(id: Long) {
        unpinConfirm = null
        scope.launch {
            runCatching { Repository.pinMessage(slug, id) }
                .onSuccess { applyPins(it, focusNewest = false) }
                .onFailure { showToast("Не удалось открепить") }
        }
    }

    /** Прокрутить к сообщению (по центру экрана) и коротко подсветить его. */
    fun jump(id: Long, flash: Boolean = true) {
        val idx = visible.indexOfFirst { it.id == id }
        if (idx < 0) { showToast("Сообщение не найдено"); return }
        scope.launch {
            val viewport = listState.layoutInfo.viewportSize.height
            runCatching { listState.animateScrollToItem(idx, -viewport / 3) }
            if (flash) {
                flashId = id
                delay(1100)
                if (flashId == id) flashId = null
            }
        }
    }

    fun openPin(p: MessageQuoteDto) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        jump(p.id)
        if (pins.size > 1) pinIndex = if (pinIndex > 0) pinIndex - 1 else pins.size - 1
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("message", text))
    }

    fun copyLink(m: ChatMessage) {
        val origin = BuildConfig.API_BASE_URL.replace("api/", "")
        copyToClipboard("${origin}community/chat/$slug?message=${m.id}")
    }

    /** Удалить у всех: сервер разрешает автору сообщения и модератору чата. */
    fun canDeleteAll(m: ChatMessage) = !m.isSystem && m.canDelete

    fun dropLocally(id: Long) {
        messages.removeAll { it.id == id }
        if (pins.removeAll { it.id == id }) clampPinIndex()
    }

    /** «Удалить у всех» или «Удалить у меня» (скрывается только для себя на всех устройствах). */
    fun delete(id: Long, forAll: Boolean) {
        closeMenu()
        scope.launch {
            runCatching { Repository.deleteMessage(slug, id, forAll) }
                .onSuccess { dropLocally(id) }
                .onFailure { showToast("Не удалось удалить сообщение") }
        }
    }

    // «Пожаловаться»: жалоба уходит сообщением в чат с администратором.
    fun complain(m: ChatMessage) {
        closeMenu()
        val quote = (m.text ?: replyPreview(m)).take(200)
        val body = "Жалоба на сообщение в чате «$chatTitle» от ${m.author ?: "участника"}: «$quote»"
        scope.launch {
            val ok = runCatching { Repository.sendMessage("admin", body) }.isSuccess
            showToast(if (ok) "Жалоба отправлена администратору" else "Не удалось отправить жалобу")
        }
    }

    fun enterSelect() {
        selected.clear()
        menuFor?.let { selected.add(it) }
        closeMenu()
        selectMode = true
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun exitSelect() {
        selectMode = false; selectDeleteMenu = false; selected.clear()
        // Выход из выбора не возвращает клавиатуру: поле ввода снова появляется без фокуса.
        focusManager.clearFocus()
        keyboard?.hide()
    }

    /** Инфо чата: клавиатура прячется до перехода и не всплывает при возврате. */
    fun openInfo() {
        focusManager.clearFocus()
        keyboard?.hide()
        onInfo()
    }

    fun toggleSelect(id: Long) {
        // Временные пузыри (ещё не на сервере) выбрать нельзя.
        if (id < 0 || messages.firstOrNull { it.id == id }?.isSystem == true) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    fun deleteSelected(forAll: Boolean) {
        val ids = selected.toList()
        exitSelect()
        scope.launch {
            var failed = false
            for (id in ids) {
                runCatching { Repository.deleteMessage(slug, id, forAll) }
                    .onSuccess { dropLocally(id) }
                    .onFailure { failed = true }
            }
            if (failed) showToast("Не все сообщения удалось удалить")
        }
    }

    fun forwardSelected() {
        val texts = messages.filter { it.id in selected }.map { m ->
            val who = if (m.mine) (Session.user?.name ?: "Я") else (m.author ?: "")
            "$who: ${m.text ?: replyPreview(m)}"
        }
        if (texts.isEmpty()) return
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texts.joinToString("\n"))
        }
        runCatching { context.startActivity(Intent.createChooser(share, null)) }
    }

    fun onLongPress(m: ChatMessage) {
        if (m.failed && !selectMode) { failedMenuFor = m.id; return }
        if (m.uploading || m.isSystem || searchMode) return
        if (selectMode) { toggleSelect(m.id); return }
        focusManager.clearFocus()
        keyboard?.hide()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        attachOpen = false
        deleteMenu = false
        menuFor = m.id
    }

    fun applyMention(p: ParticipantDto) {
        val handle = p.username?.let { if (it.startsWith("@")) it else "@$it" } ?: return
        val text = draft.text.replace(MentionRegex) { r -> (if (r.value.firstOrNull()?.isWhitespace() == true) r.value.first().toString() else "") + "$handle " }
        draft = TextFieldValue(text, TextRange(text.length))
    }

    // Кандидаты для «@» — с сервера (все, у кого есть доступ к чату), с небольшой задержкой.
    LaunchedEffect(mentionQuery) {
        mentionJob?.cancel()
        val q = mentionQuery ?: return@LaunchedEffect
        mentionJob = scope.launch {
            delay(180)
            runCatching { Repository.chatMembers(slug, q) }.getOrNull()?.let {
                mentionCandidates.clear(); mentionCandidates.addAll(it)
            }
        }
    }

    fun openSearch() {
        closeMenu()
        exitSelect()
        attachOpen = false
        focusManager.clearFocus()
        searchQuery = ""
        searchIndex = 0
        searchListOpen = false
        searchMode = true
    }

    fun closeSearch() {
        focusManager.clearFocus()
        searchMode = false
        searchListOpen = false
        searchQuery = ""
        searchIndex = 0
    }

    fun stepSearch(delta: Int) {
        val total = searchResults.size
        if (total == 0) return
        searchIndex = (searchIndex + delta).coerceIn(0, total - 1)
        focusManager.clearFocus()
        // Переход к результату — в LaunchedEffect(currentSearchId) ниже.
    }

    // Переход к текущему результату поиска при вводе и стрелках.
    LaunchedEffect(currentSearchId, searchMode) {
        if (searchMode) currentSearchId?.let { jump(it, flash = false) }
    }

    // Из инфо чата нажали «Поиск» — открываем поиск здесь.
    LaunchedEffect(ChatSearchBus.request, loaded) {
        if (loaded && ChatSearchBus.request == slug) {
            ChatSearchBus.request = null
            openSearch()
        }
    }

    LaunchedEffect(searchMode) {
        if (searchMode) {
            delay(250)
            runCatching { searchFocus.requestFocus() }
        }
    }

    /** Открыть фото: клавиатура прячется, чтобы не закрывать просмотр. */
    fun openPhoto(m: ChatMessage) {
        focusManager.clearFocus()
        keyboard?.hide()
        viewerMsg = m
    }

    BackHandler(enabled = menuFor != null || selectMode || attachOpen || viewerMsg != null || recLocked || searchMode || unpinConfirm != null) {
        when {
            viewerMsg != null -> viewerMsg = null
            unpinConfirm != null -> unpinConfirm = null
            menuFor != null -> closeMenu()
            searchListOpen -> searchListOpen = false
            searchMode -> closeSearch()
            recLocked -> cancelVoice()
            selectMode -> exitSelect()
            else -> attachOpen = false
        }
    }

    val contentTop = designTop(T.TopBarY) + 42.dp + 24.dp +
        (if (currentPin != null && !selectMode && !searchMode) 65.dp + 12.dp else 0.dp)
    val composerH = with(density) { composerHeight.toDp() }
    val recordClock = "%02d:%02d".format(recSeconds / 60, recSeconds % 60)

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        content = {
            BackdropBox(
                modifier = Modifier.fillMaxSize(),
                contentModifier = Modifier.chatBackdrop(),
                content = {
                    // MARK: Сообщения
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().nestedScroll(dragDismissKeyboard),
                        contentPadding = PaddingValues(
                            start = T.ScreenX,
                            end = T.ScreenX,
                            top = contentTop,
                            bottom = composerH + bottomPad + 12.dp,
                        ),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (!loaded) {
                            items(7) { i -> MessageSkeleton(mine = i % 3 == 0, wide = i % 2 == 0) }
                        } else {
                            itemsIndexed(visible, key = { _, m -> m.key }) { idx, m ->
                                val prev = visible.getOrNull(idx - 1)
                                val next = visible.getOrNull(idx + 1)
                                // Разделитель по дням над первым сообщением каждого дня.
                                val day = ChatDates.day(m.createdAt)
                                val newDay = prev == null || ChatDates.day(prev.createdAt) != day
                                val nextNewDay = next != null && ChatDates.day(next.createdAt) != day
                                val isTail = next?.let { nextNewDay || !sameAuthor(m, it) } ?: true
                                val isFirst = prev?.let { newDay || !sameAuthor(it, m) } ?: true
                                val topPad = when {
                                    prev == null || newDay -> 0.dp
                                    sameAuthor(prev, m) -> 4.dp
                                    else -> 12.dp
                                }
                                Column(Modifier.fillMaxWidth().animateItem(fadeInSpec = null, placementSpec = chatSpring())) {
                                if (newDay) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(top = if (prev == null) 0.dp else 12.dp, bottom = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier.height(24.dp).liquidGlass(CapsuleShape).padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(ChatDates.dayLabel(day), style = AppFont.sans(T.FsSm)) }
                                    }
                                }
                                if (m.isSystem) {
                                    SystemRow(
                                        m,
                                        Modifier.fillMaxWidth().padding(top = if (prev == null || newDay) 0.dp else 12.dp),
                                    ) { if (!selectMode) m.userId?.let { onUser(it) } }
                                } else {
                                val showAvatarColumn = !m.mine && !isDirect
                                // Появление: новое сообщение проявляется на месте, остальные сдвигаются
                                // вверх (прототип: Чат 495:16591 → 495:17748 → 495:19021) — быстрой
                                // пружиной, как в Telegram. Ключ строки постоянный: ответ сервера
                                // не запускает появление второй раз.
                                val appear = remember(m.key) { Animatable(if (m.key in initialIds) 1f else 0f) }
                                LaunchedEffect(m.key) { appear.animateTo(1f, tween(220)) }
                                val flash by animateFloatAsState(if (flashId == m.id) 1f else 0f, tween(if (flashId == m.id) 200 else 450), label = "flash")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = topPad)
                                        .background(T.Green.copy(alpha = 0.18f * flash), RoundedCornerShape(12.dp))
                                        .then(if (selectMode) Modifier.pressable(pressAlpha = 1f) { toggleSelect(m.id) } else Modifier),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    AnimatedVisibility(
                                        visible = selectMode,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        enter = fadeIn() + slideInHorizontally { -it },
                                        exit = fadeOut() + slideOutHorizontally { -it },
                                    ) {
                                        AppIcon(
                                            if (m.id in selected) AppIcons.radioOn else AppIcons.radioOff,
                                            size = 20.dp,
                                            modifier = Modifier.padding(end = 16.dp),
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp, if (m.mine) Alignment.End else Alignment.Start),
                                    ) {
                                        if (showAvatarColumn) {
                                            Box(Modifier.size(30.dp)) {
                                                if (isTail) {
                                                    MessageAvatar(
                                                        m.avatarUrl,
                                                        m.author,
                                                        Modifier.pressable(pressAlpha = 1f) {
                                                            if (!selectMode) m.userId?.let { onUser(it) }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        MessageBubble(
                                            message = m,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .graphicsLayer {
                                                    alpha = if (menuFor == m.id) 0f else appear.value.coerceIn(0f, 1f)
                                                }
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        if (selectMode) toggleSelect(m.id)
                                                        else if (m.failed) failedMenuFor = m.id
                                                    },
                                                    onLongClick = { onLongPress(m) },
                                                ),
                                            isTail = isTail,
                                            showHeader = isFirst && !isDirect,
                                            isPlaying = playingId == m.id,
                                            voiceProgress = if (playingId == m.id) voiceProgress else 0f,
                                            onPlayVoice = { if (selectMode) toggleSelect(m.id) else playVoice(m) },
                                            onOpenFile = { if (selectMode) toggleSelect(m.id) else openUrl(fileUrl(m.fileUrl)) },
                                            onOpenImage = { if (selectMode) toggleSelect(m.id) else if (m.failed) failedMenuFor = m.id else openPhoto(m) },
                                            onLongPress = { onLongPress(m) },
                                            onToggleReaction = if (selectMode || m.uploading) null else { e -> react(m.id, e) },
                                            highlight = if (searchMode) searchQuery else null,
                                        )
                                    }
                                }
                                }
                                }
                            }
                        }
                    }

                    // Читаете старые, а пришли новые — плашка поверх ленты (ничего не сдвигает).
                    androidx.compose.animation.AnimatedVisibility(
                        visible = newBelow > 0 && !searchMode && !selectMode,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = composerH + bottomPad + 8.dp),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .liquidGlass(CapsuleShape)
                                .pressable {
                                    newBelow = 0
                                    scope.launch { runCatching { listState.animateScrollToItem(visible.size - 1) } }
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(if (newBelow == 1) "Новое сообщение" else "Новые сообщения · $newBelow", style = AppFont.sans(T.FsBase))
                            Text("↓", style = AppFont.sans(T.FsBase, FontWeight.SemiBold))
                        }
                    }
                },
                overlay = {
                    // MARK: Шапка
                    ChatTopBar(
                        selectMode = selectMode,
                        selectedCount = selected.size,
                        title = chatTitle,
                        subtitle = chatSubtitle,
                        avatar = chatAvatar,
                        loaded = loaded,
                        pinned = currentPin,
                        pinCount = pins.size,
                        pinIndex = pinIndex,
                        canPin = canPin,
                        searchMode = searchMode,
                        searchQuery = searchQuery,
                        searchFocus = searchFocus,
                        onSearchQuery = { searchQuery = it; searchIndex = 0 },
                        onCloseSearch = { closeSearch() },
                        onBack = onBack,
                        onInfo = { openInfo() },
                        onCancelSelect = { exitSelect() },
                        onOpenPin = { openPin(it) },
                        onUnpin = { unpinConfirm = it },
                    )

                    // MARK: Нижняя панель
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = T.ScreenX)
                            .padding(bottom = bottomPad)
                            .onSizeChanged { composerHeight = it.height }
                            .padding(top = 12.dp),
                    ) {
                        when {
                            searchMode -> SearchBottomBar(
                                query = searchQuery,
                                total = searchResults.size,
                                index = searchIndex,
                                listOpen = searchListOpen,
                                onToggleList = { focusManager.clearFocus(); searchListOpen = !searchListOpen },
                                onUp = { stepSearch(1) },
                                onDown = { stepSearch(-1) },
                            )
                            selectMode -> Row(Modifier.fillMaxWidth()) {
                                GlassCircleButton(AppIcons.trashX, "Удалить выбранные", iconSize = 24.dp, iconColor = T.Error) {
                                    if (selected.isNotEmpty()) selectDeleteMenu = !selectDeleteMenu
                                }
                                Spacer(Modifier.weight(1f))
                                GlassCircleButton(AppIcons.forward, "Переслать", iconSize = 24.dp) { forwardSelected() }
                            }
                            recording && recPaused -> PausedRecordingBar(
                                clock = recordClock,
                                onCancel = { cancelVoice() },
                                onSend = { stopAndSendVoice() },
                                onResume = { resumeRecording() },
                            )
                            else -> Box(Modifier.fillMaxWidth()) {
                                val recProgress by animateFloatAsState(if (recording) 1f else 0f, figmaTween(), label = "rec")
                                val recShown = recProgress.coerceIn(0f, 1f)
                                // Поле ввода остаётся в иерархии во время записи: жест
                                // удержания микрофона продолжается, пока поверх показана
                                // плашка записи.
                                Row(
                                    modifier = Modifier.fillMaxWidth().alpha(1f - recShown),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Поворачивается только «+», стекло остаётся на месте.
                                    val plusAngle by animateFloatAsState(if (attachOpen) 45f else 0f, ChatSpring, label = "plus")
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .liquidGlass(CircleShape)
                                            .pressable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                attachOpen = !attachOpen
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppIcon(AppIcons.plus, size = 20.dp, modifier = Modifier.rotate(plusAngle))
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                            .liquidGlass(T.ShapeInput)
                                            .padding(
                                                start = 16.dp,
                                                end = if (showSend) 0.dp else 12.dp,
                                                top = if (replyTo != null || pendingImages.isNotEmpty() || draftIsAuthorMessage ||
                                                    (mentionMatches.isNotEmpty() && !attachOpen)
                                                ) 12.dp else 0.dp,
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        // Упоминания (Figma 337:34963): список в том же стекле, над текстом.
                                        if (mentionMatches.isNotEmpty() && !attachOpen) {
                                            MentionList(mentionMatches) { applyMention(it) }
                                        }
                                        if (privateHint != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                AppIcon(AppIcons.eyeClosed, size = 14.dp, tint = Secondary)
                                                Text(privateHint, style = AppFont.sans(T.FsSm, color = Secondary))
                                            }
                                        }
                                        if (pendingImages.isNotEmpty()) {
                                            PendingPhotosStrip(
                                                uris = pendingImages,
                                                onRemove = { pendingImages.remove(it) },
                                            )
                                        }
                                        replyTo?.let { reply ->
                                            ComposerQuote(
                                                author = reply.author ?: Session.user?.name ?: "Вы",
                                                text = reply.text ?: replyPreview(reply),
                                                onClose = { replyTo = null },
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.heightIn(min = 42.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            BasicTextField(
                                                value = draft,
                                                onValueChange = { draft = it },
                                                maxLines = 5,
                                                textStyle = AppFont.sans(T.FsBase),
                                                cursorBrush = SolidColor(T.Green),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(vertical = 11.dp)
                                                    .focusRequester(inputFocus),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (draft.text.isEmpty()) {
                                                            Text(
                                                                "Напишите что-нибудь",
                                                                style = AppFont.sans(T.FsBase, color = Color.White.copy(alpha = 0.4f)),
                                                                maxLines = 1,
                                                            )
                                                        }
                                                        inner()
                                                    }
                                                },
                                            )
                                            AnimatedVisibility(
                                                visible = showSend,
                                                enter = fadeIn(figmaFade()),
                                                exit = fadeOut(figmaFade()),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(T.Green, CircleShape)
                                                        .pressable { send() },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AppIcon(AppIcons.sendTriangle, size = 20.dp, tint = Color.White, modifier = Modifier.offset(x = 1.dp))
                                                }
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = !showSend,
                                        enter = fadeIn(figmaFade()),
                                        exit = fadeOut(figmaFade()),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .liquidGlass(CircleShape)
                                                .pointerInput(Unit) {
                                                    val cancelPx = 110.dp.toPx()
                                                    val lockPx = 90.dp.toPx()
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown()
                                                        down.consume()
                                                        onMicDown()
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (!change.pressed) {
                                                                change.consume()
                                                                break
                                                            }
                                                            onMicDrag(change.position - down.position, cancelPx, lockPx)
                                                            change.consume()
                                                        }
                                                        onMicUp()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppIcon(AppIcons.mic, size = 24.dp)
                                        }
                                    }
                                }
                                // Плашка записи проявляется поверх поля (прототип: Чат 495:16053 →
                                // 495:16591, SMART_ANIMATE 800 мс).
                                AnimatedVisibility(visible = recording, enter = fadeIn(figmaFade()), exit = fadeOut(figmaFade())) {
                                    RecordingBar(
                                        clock = recordClock,
                                        locked = recLocked,
                                        // Лямбды читаются только при раскладке и отрисовке: движение
                                        // пальца не перекомпоновывает весь экран чата.
                                        drag = { recordDrag },
                                        amplitude = { recorder?.runCatching { maxAmplitude }?.getOrNull() ?: 0 },
                                        onCancel = { cancelVoice() },
                                        onPause = { pauseRecording() },
                                        onSend = { stopAndSendVoice() },
                                    )
                                }
                            }
                        }
                    }

                    // popup/отправка: над «+» (−54 от низа поля).
                    AnimatedVisibility(
                        visible = attachOpen && !selectMode && !recording,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = T.ScreenX, bottom = bottomPad + 54.dp),
                        // Прототип: Button/add → popup/отправка, DISSOLVE 800 мс, ease-in-out-back.
                        enter = fadeIn(figmaFade()),
                        exit = fadeOut(figmaFade()),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(193.dp)
                                .liquidGlass(T.ShapeCard, Fills.Black2)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MenuLabel("Изображение", AppIcons.picture, iconSize = 24.dp) {
                                attachOpen = false
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            MenuLabel("Файл", AppIcons.doc, iconSize = 20.dp) {
                                attachOpen = false
                                filePicker.launch("*/*")
                            }
                        }
                    }

                    // Режим выбора: «Удалить у всех» / «Удалить у меня» над корзиной (Figma 337:29172).
                    AnimatedVisibility(
                        visible = selectMode && selectDeleteMenu,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = T.ScreenX, bottom = bottomPad + 54.dp),
                        enter = fadeIn(figmaFade()) + scaleIn(initialScale = 0.95f, transformOrigin = TransformOrigin(0f, 1f)),
                        exit = fadeOut(figmaFade()),
                    ) {
                        val chosen = messages.filter { it.id in selected }
                        DeleteChoicePanel(
                            canDeleteAll = chosen.isNotEmpty() && chosen.all { canDeleteAll(it) },
                            onDeleteAll = { deleteSelected(forAll = true) },
                            onDeleteMine = { deleteSelected(forAll = false) },
                        )
                    }
                },
            )
        },
        overlay = {
            // MARK: Меню сообщения
            val menuMsg = remember { mutableStateOf<ChatMessage?>(null) }
            val current = menuFor?.let { id -> messages.firstOrNull { it.id == id } }
            if (current != null) menuMsg.value = current
            AnimatedVisibility(visible = current != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
                // Пока меню растворяется, в нём та же версия сообщения, что и в ленте:
                // выбранная реакция видна сразу, а не после закрытия меню.
                val m = menuMsg.value?.let { old -> messages.firstOrNull { it.id == old.id } ?: old }
                if (m != null) {
                    MessageMenuOverlay(
                        message = m,
                        isDirect = isDirect,
                        deleteMenu = deleteMenu,
                        canDelete = canDeleteAll(m),
                        canPin = canPin && !m.isPrivate,
                        canComplain = !Session.isAdmin && !m.mine,
                        onClose = { closeMenu() },
                        onReact = { react(m.id, it) },
                        onReply = {
                            replyTo = m
                            closeMenu()
                            runCatching { inputFocus.requestFocus() }
                        },
                        onCopy = {
                            copyToClipboard(m.text ?: "")
                            closeMenu()
                            showToast("Скопировано")
                        },
                        onPin = { togglePin(m.id) },
                        onCopyLink = {
                            copyLink(m)
                            closeMenu()
                            showToast("Ссылка скопирована")
                        },
                        onComplain = { complain(m) },
                        onDeleteMenu = { deleteMenu = true },
                        onDeleteAll = { delete(m.id, forAll = true) },
                        onDeleteMine = { delete(m.id, forAll = false) },
                        onSelect = { enterSelect() },
                    )
                }
            }

            // MARK: Результаты поиска списком
            AnimatedVisibility(visible = searchMode && searchListOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
                SearchResultsList(
                    results = searchResults.asReversed().mapNotNull { id -> messages.firstOrNull { it.id == id } },
                    query = searchQuery,
                    topPad = designTop(T.TopBarY) + 42.dp + 16.dp,
                    bottomPad = bottomPad + 42.dp + 24.dp,
                    myName = Session.user?.name,
                    onPick = { idx, m ->
                        searchIndex = idx
                        searchListOpen = false
                        jump(m.id, flash = false)
                    },
                )
            }

            // MARK: Подтверждение открепления
            ConfirmDialog(
                open = unpinConfirm != null,
                title = "Открепить сообщение?",
                text = "Сообщение пропадёт из закреплённых у всех участников",
                confirmLabel = "Открепить",
                onConfirm = { unpinConfirm?.let { unpin(it.id) } },
                onCancel = { unpinConfirm = null },
            )

            // MARK: Неотправленное сообщение
            ConfirmDialog(
                open = failedMenuFor != null,
                title = "Сообщение не отправлено",
                text = "Проверьте соединение и попробуйте ещё раз",
                extraLabel = "Повторить",
                onExtra = { failedMenuFor?.let { retryFailed(it) } },
                confirmLabel = "Удалить",
                onConfirm = { failedMenuFor?.let { discardFailed(it) } },
                onCancel = { failedMenuFor = null },
            )

            // MARK: Просмотр фото
            // Прототип: сообщение с фото → «Чат нажатие на фото» и назад — 800 мс.
            AnimatedVisibility(visible = viewerMsg != null, enter = fadeIn(figmaFade()), exit = fadeOut(figmaFade())) {
                val vm = remember { mutableStateOf<ChatMessage?>(null) }
                viewerMsg?.let { vm.value = it }
                vm.value?.let { m ->
                    PhotoViewer(
                        imageUrl = fileUrl(m.imageUrl),
                        author = if (m.mine) "Вы" else (m.author ?: ""),
                        time = ChatDates.stamp(m.createdAt, m.time),
                        canDelete = canDeleteAll(m),
                        onClose = { viewerMsg = null },
                        onDownload = { openUrl(fileUrl(m.imageUrl)) },
                        onDelete = { viewerMsg = null; delete(m.id, forAll = true) },
                    )
                }
            }

            // MARK: Тост
            AnimatedVisibility(
                visible = toast != null,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop() + 76.dp),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                val last = remember { mutableStateOf("") }
                toast?.let { last.value = it }
                Box(
                    modifier = Modifier.height(32.dp).liquidGlass(RoundedCornerShape(24.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(last.value, style = AppFont.sans(T.FsBase)) }
            }
        },
    )
}

// MARK: - Шапка

@Composable
private fun BoxScope.ChatTopBar(
    selectMode: Boolean,
    selectedCount: Int,
    title: String,
    subtitle: String,
    avatar: String?,
    loaded: Boolean,
    pinned: MessageQuoteDto?,
    pinCount: Int,
    pinIndex: Int,
    canPin: Boolean,
    searchMode: Boolean,
    searchQuery: String,
    searchFocus: FocusRequester,
    onSearchQuery: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onCancelSelect: () -> Unit,
    onOpenPin: (MessageQuoteDto) -> Unit,
    onUnpin: (MessageQuoteDto) -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(top = designTop(T.TopBarY))
            .padding(horizontal = T.ScreenX),
    ) {
        val mode = when {
            searchMode -> 2
            selectMode -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "chat-top",
        ) { m ->
            when (m) {
                2 -> SearchTopBar(searchQuery, searchFocus, onSearchQuery, onCloseSearch)
                1 -> Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassPill { Text("Выбрано $selectedCount", style = AppFont.sans(T.FsBase, FontWeight.SemiBold)) }
                    Spacer(Modifier.weight(1f))
                    GlassPill(Modifier.pressable { onCancelSelect() }) { Text("Отмена", style = AppFont.sans(T.FsBase)) }
                }
                else -> Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack)
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .height(42.dp)
                                .liquidGlass(RoundedCornerShape(24.dp))
                                .pressable { onInfo() }
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(title, style = AppFont.sans(T.FsBase, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (subtitle.isNotEmpty()) {
                                Text(subtitle, style = AppFont.sans(10.sp, color = Secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // Иконка чата открывает то же инфо, что и название.
                    ReportCover(avatar, Modifier.size(42.dp).clip(CircleShape).pressable { onInfo() }, loading = !loaded)
                }
            }
        }
        AnimatedVisibility(
            visible = pinned != null && !selectMode && !searchMode,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            val last = remember { mutableStateOf<MessageQuoteDto?>(null) }
            pinned?.let { last.value = it }
            last.value?.let { p -> PinnedBar(p, pinCount, pinIndex, canPin, onOpenPin, onUnpin) }
        }
    }
}

/** Шапка поиска: стеклянное поле с лупой и «Отмена». */
@Composable
private fun SearchTopBar(
    query: String,
    focus: FocusRequester,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .liquidGlass(RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(AppIcons.search, size = 18.dp, tint = Secondary)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = AppFont.sans(T.FsBase),
                cursorBrush = SolidColor(T.Green),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Поиск", style = AppFont.sans(T.FsBase, color = Color.White.copy(alpha = 0.4f)), maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .pressable { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.close, size = 10.dp) }
            }
        }
        GlassPill(Modifier.pressable { onClose() }) { Text("Отмена", style = AppFont.sans(T.FsBase)) }
    }
}

/** Нижняя панель поиска (вместо поля ввода): список результатов, «N из M», стрелки. */
@Composable
private fun SearchBottomBar(
    query: String,
    total: Int,
    index: Int,
    listOpen: Boolean,
    onToggleList: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .alpha(if (total == 0) 0.5f else 1f)
                .liquidGlass(CircleShape)
                .then(if (total > 0) Modifier.pressable { onToggleList() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (listOpen) {
                AppIcon(AppIcons.chat, size = 20.dp)
            } else {
                // «Список»: три полоски.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.size(16.dp, 1.5.dp).background(Color.White, CapsuleShape)) }
                }
            }
        }
        Text(
            when {
                query.trim().isEmpty() -> "Введите текст для поиска"
                total == 0 -> "Ничего не найдено"
                else -> "${index.coerceAtMost(total - 1) + 1} из $total"
            },
            style = AppFont.sans(T.FsBase),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
        Row(Modifier.height(42.dp).liquidGlass(CapsuleShape), verticalAlignment = Alignment.CenterVertically) {
            SearchArrow(up = true, enabled = index + 1 < total, onClick = onUp)
            SearchArrow(up = false, enabled = index > 0 && total > 0, onClick = onDown)
        }
    }
}

@Composable
private fun SearchArrow(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp, 42.dp)
            .then(if (enabled) Modifier.pressable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            AppIcons.chevronRight,
            size = 20.dp,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.rotate(if (up) -90f else 90f),
        )
    }
}

/** Результаты поиска списком (как в Телеграме): от новых к старым. */
@Composable
private fun SearchResultsList(
    results: List<ChatMessage>,
    query: String,
    topPad: androidx.compose.ui.unit.Dp,
    bottomPad: androidx.compose.ui.unit.Dp,
    myName: String?,
    onPick: (Int, ChatMessage) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground()
            .padding(top = topPad),
        contentPadding = PaddingValues(bottom = bottomPad),
    ) {
        itemsIndexed(results, key = { _, m -> m.id }) { idx, m ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(pressAlpha = 0.7f) { onPick(idx, m) }
                        .padding(horizontal = T.ScreenX, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AvatarCircle(fileUrl(m.avatarUrl), size = 36.dp, name = if (m.mine) (myName ?: "Вы") else m.author)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (m.mine) "Вы" else (m.author ?: "Участник"),
                                style = AppFont.sans(T.FsBase, FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(searchDate(m), style = AppFont.sans(T.FsSm, color = Secondary), modifier = Modifier.padding(start = 8.dp))
                        }
                        val text = m.text ?: m.fileName ?: replyPreview(m)
                        Text(
                            buildAnnotatedString {
                                append(text)
                                val q = query.trim()
                                val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
                                if (i >= 0) addStyle(SpanStyle(color = T.Text, fontWeight = FontWeight.SemiBold), i, i + q.length)
                            },
                            style = AppFont.sans(T.FsBase, color = Secondary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (idx < results.size - 1) {
                    Box(Modifier.padding(start = T.ScreenX + 48.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                }
            }
        }
    }
}

/** Служебное сообщение «Имя зашел в чат / вышел из чата»: имя открывает профиль. */
@Composable
private fun SystemRow(m: ChatMessage, modifier: Modifier, onUser: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .heightIn(min = 24.dp)
                .liquidGlass(CapsuleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                m.author ?: "Пользователь",
                style = AppFont.sans(T.FsSm, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(if (m.userId != null) Modifier.pressable { onUser() } else Modifier),
            )
            Text(m.eventText, style = AppFont.sans(T.FsSm), maxLines = 1)
        }
    }
}

/** Список упоминаний внутри поля ввода (Figma 337:34963): аватар, имя, @ник, разделители. */
@Composable
private fun MentionList(users: List<ParticipantDto>, onPick: (ParticipantDto) -> Unit) {
    Column(Modifier.padding(end = 12.dp)) {
        users.forEachIndexed { i, u ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pressable(pressAlpha = 0.7f) { onPick(u) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarCircle(fileUrl(u.avatarUrl), size = 28.dp, name = u.name.ifEmpty { u.username })
                Text(u.name, style = AppFont.sans(T.FsBase, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                u.username?.let {
                    Text(it, style = AppFont.sans(T.FsBase, color = Secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (i < users.size - 1) MenuDivider()
        }
    }
}

/** «Удалить у всех» / «Удалить у меня»: стекло 193, красный текст, разделитель. */
@Composable
private fun DeleteChoicePanel(canDeleteAll: Boolean, onDeleteAll: () -> Unit, onDeleteMine: () -> Unit) {
    Column(
        modifier = Modifier
            .width(193.dp)
            .liquidGlass(T.ShapeCard, Fills.Black2)
            .pressable(pressAlpha = 1f) {}
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (canDeleteAll) {
            Text(
                "Удалить у всех",
                style = AppFont.sans(T.FsBase, color = T.Error),
                modifier = Modifier.fillMaxWidth().pressable { onDeleteAll() },
            )
            MenuDivider()
        }
        Text(
            "Удалить у меня",
            style = AppFont.sans(T.FsBase, color = T.Error),
            modifier = Modifier.fillMaxWidth().pressable { onDeleteMine() },
        )
    }
}

/** Стеклянная пилюля шапки («Выбрано N», «Отмена»): 42, отступы 24. */
@Composable
private fun GlassPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.height(42.dp).liquidGlass(RoundedCornerShape(24.dp)).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Закреплённое сообщение: стекло, радиус 24, линия 1 dp слева, заголовок
 * SemiBold 14 и текст 14; крестик 12 справа. Закрепов несколько — линия
 * делится на отрезки (как в Телеграме), текущий ярче; тап ведёт к сообщению
 * и переключает на предыдущий закреп. Крестик — только у админа и автора отчёта.
 */
@Composable
private fun PinnedBar(
    p: MessageQuoteDto,
    count: Int,
    index: Int,
    canPin: Boolean,
    onOpen: (MessageQuoteDto) -> Unit,
    onUnpin: (MessageQuoteDto) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .height(65.dp)
            .liquidGlass(T.ShapeCard)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f).pressable(pressAlpha = 1f) { onOpen(p) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val n = count.coerceAtLeast(1)
            Column(Modifier.width(2.dp).height(40.dp), verticalArrangement = Arrangement.spacedBy(if (n > 1) 2.dp else 0.dp)) {
                repeat(n) { i ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.White.copy(alpha = if (i == index || n == 1) 1f else 0.35f), CapsuleShape),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (count > 1) "Закрепленное сообщение #${index + 1}" else "Закрепленное сообщение",
                    style = AppFont.sans(T.FsBase, FontWeight.SemiBold),
                    maxLines = 1,
                )
                AnimatedContent(
                    targetState = p,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    contentKey = { it.id },
                    label = "pin-text",
                ) { q ->
                    Text(q.text ?: "", style = AppFont.sans(T.FsBase), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (canPin) {
            Box(
                modifier = Modifier.size(38.dp, 36.dp).pressable(pressAlpha = 1f) { onUnpin(p) },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.close, size = 12.dp) }
        }
    }
}

// MARK: - Сообщения

@Composable
private fun MessageAvatar(url: String?, name: String?, modifier: Modifier = Modifier) {
    val photo = fileUrl(url)
    val letter: @Composable BoxScope.() -> Unit = {
        Box(Modifier.fillMaxSize().background(hex("#d9d9d9")), contentAlignment = Alignment.Center) {
            Text((name ?: "?").take(1).uppercase(), style = AppFont.heading(13.sp))
        }
    }
    Box(modifier = modifier.size(30.dp).clip(CircleShape)) {
        // Пока фото грузится — мигающий круг; буква на сером — только если фото нет.
        if (photo != null) RemoteImage(photo, Modifier.fillMaxSize(), loading = true, fallback = letter) else letter()
    }
}

@Composable
private fun MessageSkeleton(mine: Boolean, wide: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            SkeletonBox(Modifier.size(30.dp), shape = CircleShape)
            Spacer(Modifier.width(4.dp))
        }
        SkeletonBox(
            Modifier.width(if (wide) 230.dp else 150.dp).height(if (wide) 64.dp else 44.dp),
            shape = RoundedCornerShape(24.dp),
        )
    }
}

/** Цитата ответа внутри поля ввода (Figma 337:30778). */
@Composable
private fun ComposerQuote(author: String, text: String, onClose: () -> Unit) {
    Box(modifier = Modifier.padding(end = 12.dp)) {
        ReplyQuote(author, text)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(28.dp, 32.dp)
                .pressable(pressAlpha = 1f) { onClose() },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.close, size = 12.dp) }
    }
}

@Composable
private fun MenuLabel(
    title: String,
    icon: IconDef,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    color: Color = T.Text,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            AppIcon(icon, size = iconSize, tint = color)
        }
        Text(title, style = AppFont.sans(T.FsBase, color = color), maxLines = 1)
    }
}

// MARK: - Запись

/**
 * Идёт запись (Figma 337:32380 / замок 337:32783): стеклянная плашка с
 * красной точкой и временем, большая зелёная кнопка 75 с ореолом 96.
 */
@Composable
private fun RecordingBar(
    clock: String,
    locked: Boolean,
    drag: () -> Offset,
    amplitude: () -> Int,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onSend: () -> Unit,
) {
    val density = LocalDensity.current
    val pulse = rememberInfiniteTransition(label = "rec-pulse")
    val dotAlpha by pulse.animateFloat(
        1f, 0.25f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot",
    )
    val arrowBounce by pulse.animateFloat(
        1f, -3f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrow",
    )
    // Ореол «дышит» в такт голосу: громкость сглаживается покадрово, читается при отрисовке.
    val level = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val raw = (amplitude() / 22000f).coerceIn(0f, 1f)
            level.floatValue += (raw * raw - level.floatValue) * 0.2f
        }
    }
    val breath by pulse.animateFloat(
        -0.03f, 0.03f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 37.dp)
                .height(44.dp)
                .liquidGlass(T.ShapeInput)
                .padding(start = 16.dp, end = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.padding(end = 4.dp).size(8.dp).graphicsLayer { alpha = dotAlpha }.background(T.Error, CircleShape))
            Text(clock, style = AppFont.sans(T.FsBase))
            Spacer(Modifier.width(8.dp))
            if (locked) {
                Spacer(Modifier.weight(1f))
                Text("Отмена", style = AppFont.sans(T.FsBase), modifier = Modifier.pressable { onCancel() })
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .offset { IntOffset((min(0f, drag().x) / 2.2f).roundToInt(), 0) }
                        .graphicsLayer {
                            val dx = min(0f, drag().x) / density.density
                            alpha = (1f + dx / 130f).coerceIn(0f, 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppIcon(AppIcons.back, size = 14.dp)
                    Text("Влево — отмена", style = AppFont.sans(T.FsBase), maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // Большая кнопка: ореол «active» 96 и зелёный круг 75.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 11.dp)
                .requiredSize(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val k = 1f + breath + 0.28f * level.floatValue
                        scaleX = k
                        scaleY = k
                        alpha = 0.55f
                    }
                    .fillShape(Fills.Active, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(75.dp)
                    .background(T.Green, CircleShape)
                    .then(if (locked) Modifier.pressable(pressAlpha = 1f) { onSend() } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (locked) {
                    AppIcon(AppIcons.back, size = 26.dp, tint = Color.White, modifier = Modifier.rotate(90f))
                } else {
                    AppIcon(AppIcons.mic, size = 24.dp, tint = Color.White)
                }
            }
        }

        // Замок (тянуть вверх) или пауза (в замке).
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-13).dp, y = (-90).dp)
                    .size(48.dp, 42.dp)
                    .liquidGlass(RoundedCornerShape(24.dp))
                    .pressable { onPause() },
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(3.dp, 13.dp).background(Color.White, CapsuleShape))
                    Box(Modifier.size(3.dp, 13.dp).background(Color.White, CapsuleShape))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val lift = (drag().y / 2.5f).coerceIn(-36.dp.toPx(), 0f)
                        IntOffset((-13).dp.roundToPx(), ((-118).dp.toPx() + lift).roundToInt())
                    }
                    .width(48.dp)
                    .liquidGlass(CapsuleShape)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppIcon(AppIcons.lockOpen, size = 20.dp)
                AppIcon(
                    AppIcons.back, size = 16.dp,
                    modifier = Modifier.graphicsLayer { translationY = arrowBounce * density.density }.rotate(90f),
                )
            }
        }
    }
}

/**
 * Запись на паузе (Figma 337:33184): корзина, волна с плашкой «▶ 1:30»,
 * зелёная «отправить»; над ней — микрофон для продолжения записи.
 */
@Composable
private fun PausedRecordingBar(clock: String, onCancel: () -> Unit, onSend: () -> Unit, onResume: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCircleButton(AppIcons.trashX, "Удалить запись", iconSize = 24.dp, iconColor = T.Error) { onCancel() }
            Box(
                modifier = Modifier.weight(1f).height(42.dp).liquidGlass(T.ShapeInput),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 54.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(7.dp).clipToBounds()) {
                        val w = 1.5.dp.toPx()
                        val gap = 1.5.dp.toPx()
                        val unit = 1.dp.toPx()
                        var x = 0f
                        var i = 0
                        while (x < size.width && i < 64) {
                            val h = (2 + (abs(sin(i * 1.9)) * 5).roundToInt()) * unit
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.4f),
                                topLeft = Offset(x, (size.height - h) / 2),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(w / 2, w / 2),
                            )
                            x += w + gap
                            i++
                        }
                    }
                    Row(
                        modifier = Modifier
                            .height(17.dp)
                            .background(hex("#5c5c5c"), CapsuleShape)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppIcon(AppIcons.play, size = 9.dp, tint = Color.White.copy(alpha = 0.4f))
                        Text(clock, style = AppFont.sans(10.sp, color = Secondary))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(42.dp)
                        .background(T.Green, CircleShape)
                        .pressable { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcons.sendTriangle, size = 20.dp, tint = Color.White, modifier = Modifier.offset(x = 1.dp))
                }
            }
        }
        GlassCircleButton(
            AppIcons.mic,
            contentDescription = "Продолжить запись",
            modifier = Modifier.align(Alignment.TopEnd).offset(y = (-66).dp),
            iconSize = 24.dp,
        ) { onResume() }
    }
}

// MARK: - Меню сообщения

/**
 * «Чат зажатие сообщения» (Figma 337:36256 / 337:36316): размытие 12.9 и
 * затемнение #070707 70 %, сверху плашка реакций, затем сообщение и
 * стеклянное меню 193.
 */
@Composable
private fun MessageMenuOverlay(
    message: ChatMessage,
    isDirect: Boolean,
    deleteMenu: Boolean,
    canDelete: Boolean,
    canPin: Boolean,
    canComplain: Boolean,
    onClose: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onCopyLink: () -> Unit,
    onComplain: () -> Unit,
    onDeleteMenu: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteMine: () -> Unit,
    onSelect: () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing)) }
    val mine = message.mine
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .backdropBlur(13.dp)
                .background(hex("#070707").copy(alpha = 0.7f))
                .pressable(pressAlpha = 1f) { onClose() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (mine || isDirect) T.ScreenX else T.ScreenX + 34.dp,
                    end = T.ScreenX,
                )
                .graphicsLayer {
                    val s = 0.96f + 0.04f * anim.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0.5f)
                },
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .background(T.Surface, CapsuleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                REACTIONS.forEach { r ->
                    // Своя реакция подсвечена: повторное нажатие снимает её.
                    val mineReaction = message.reactions.any { it.emoji == r && it.mine }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (mineReaction) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            .pressable { onReact(r) },
                        contentAlignment = Alignment.Center,
                    ) { Text(r, fontSize = 20.sp, maxLines = 1, softWrap = false) }
                }
            }

            MessageBubble(message = message, isTail = true, showHeader = !isDirect)

            AnimatedContent(
                targetState = deleteMenu,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.95f, transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0f))) togetherWith
                        fadeOut(tween(150))
                },
                label = "menu-panel",
            ) { del ->
                Column(
                    modifier = Modifier
                        .width(193.dp)
                        .liquidGlass(T.ShapeCard)
                        .pressable(pressAlpha = 1f) {}
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (del) {
                        if (canDelete) {
                            Text(
                                "Удалить у всех",
                                style = AppFont.sans(T.FsBase, color = T.Error),
                                modifier = Modifier.fillMaxWidth().pressable { onDeleteAll() },
                            )
                            MenuDivider()
                        }
                        Text(
                            "Удалить у меня",
                            style = AppFont.sans(T.FsBase, color = T.Error),
                            modifier = Modifier.fillMaxWidth().pressable { onDeleteMine() },
                        )
                    } else {
                        MenuLabel("Ответить", AppIcons.reply, onClick = onReply)
                        MenuLabel("Скопировать", AppIcons.copy, onClick = onCopy)
                        if (canPin) MenuLabel(if (message.pinned) "Открепить" else "Закрепить", AppIcons.pin, onClick = onPin)
                        MenuLabel("Копировать ссылку", AppIcons.link, onClick = onCopyLink)
                        if (canComplain) MenuLabel("Пожаловаться", AppIcons.report, onClick = onComplain)
                        MenuLabel("Удалить", AppIcons.trashX, color = T.Error, onClick = onDeleteMenu)
                        MenuDivider()
                        MenuLabel("Выбрать", AppIcons.selectMultiple, onClick = onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
}

// MARK: - Просмотр фото

@Composable
private fun PhotoViewer(
    imageUrl: String?,
    author: String,
    time: String,
    canDelete: Boolean,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pressable(pressAlpha = 1f) {},
    ) {
        if (imageUrl != null) {
            coil.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = designTop(T.TopBarY))
                .padding(horizontal = T.ScreenX),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = onClose)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (author.isNotEmpty()) Text(author, style = AppFont.sans(T.FsBase, FontWeight.SemiBold), maxLines = 1)
                if (time.isNotEmpty()) Text(time, style = AppFont.sans(T.FsSm, color = Secondary))
            }
            GlassCircleButton(AppIcons.downloadCloud, "Скачать", iconSize = 24.dp) { onDownload() }
        }
        if (canDelete) {
            GlassCircleButton(
                AppIcons.trashX,
                contentDescription = "Удалить фото",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = T.ScreenX, bottom = designBottom(45.dp)),
                iconSize = 24.dp,
                iconColor = T.Error,
            ) { onDelete() }
        }
    }
}

// MARK: - Фон чата

/**
 * Фон «Чат»: градиент экрана, свечение и узор из треугольников 24.6
 * (шаг 87.6, ряды через один сдвинуты на 32.9; заливка — белый 2 % → 0).
 */
private fun Modifier.chatBackdrop(): Modifier = this
    .screenBackground()
    .drawWithCache {
        val d = density
        val step = 87.6f * d
        val side = 24.6f * d
        val rowShift = 32.9f * d
        // Фаза узора как в макете: ряд A на y=21.9 с x≡70.1, ряд B — x≡37.2.
        val y0 = 21.9f * d - step * 2
        val triangles = mutableListOf<Pair<Path, Brush>>()
        var row = 0
        var y = y0
        while (y < size.height + side) {
            val xBase = if (row % 2 == 0) 37.2f * d else 37.2f * d + rowShift
            var x = (xBase % step) - step
            while (x < size.width + side) {
                val p = Path().apply {
                    moveTo(x + side / 2, y)
                    lineTo(x + side * 0.933f, y + side * 0.75f)
                    lineTo(x + side * 0.067f, y + side * 0.75f)
                    close()
                }
                val b = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0f)),
                    startY = y,
                    endY = y + side,
                )
                triangles.add(p to b)
                x += step
            }
            y += step
            row += 1
        }
        onDrawBehind { triangles.forEach { (p, b) -> drawPath(p, b) } }
    }

/** Превью выбранных фото над полем ввода: миниатюра и крестик для удаления. */
@Composable
private fun PendingPhotosStrip(uris: List<Uri>, onRemove: (Uri) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uris, key = { it.toString() }) { uri ->
            Box(modifier = Modifier.size(64.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { onRemove(uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcons.close, size = 12.dp, tint = Color.White)
                }
            }
        }
    }
}
