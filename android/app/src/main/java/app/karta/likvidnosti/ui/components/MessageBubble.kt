package app.karta.likvidnosti.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.runtime.key
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.hex
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Реакция на сообщение. */
data class Reaction(val emoji: String, val count: Int, val mine: Boolean)

/** UI-модель сообщения чата (из MessageDto бэкенда, 1:1 с iOS ChatMessage). */
data class ChatMessage(
    val id: Long,
    val mine: Boolean,
    val userId: Long? = null,
    val author: String? = null,
    val role: String? = null,
    val avatarUrl: String? = null,
    val text: String? = null,
    val time: String? = null,
    val read: Boolean = false,
    val voiceUrl: String? = null,
    val voiceDuration: String? = null,
    val fileUrl: String? = null,
    val fileExt: String? = null,
    val fileName: String? = null,
    val fileSize: String? = null,
    val imageUrl: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    /** Все фото альбома (2 и больше — сетка 2 колонки). */
    val imageUrls: List<String> = emptyList(),
    /** Локальные фото, пока они загружаются (спиннер поверх превью). */
    val localImageUris: List<Uri> = emptyList(),
    val uploading: Boolean = false,
    val replyAuthor: String? = null,
    val replyText: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: String? = null,
    /** Служебное: "join" — зашёл в чат, "leave" — вышел. */
    val event: String? = null,
    /** Личное сообщение автору отчёта (/текст/) и ответы на него. */
    val isPrivate: Boolean = false,
    val privateLabel: String? = null,
    /** Сервер разрешает удалить у всех. */
    val canDelete: Boolean = false,
) {
    val isSystem: Boolean get() = event != null

    /** Текст служебного сообщения без имени. */
    val eventText: String get() = if (event == "leave") "вышел из чата" else "зашел в чат"
}

private val MentionPattern = Regex("@[\\p{L}\\p{N}_.]+")
private val SearchHighlight = Color(0xFFD9A400).copy(alpha = 0.55f)

/** Текст с выделенными упоминаниями «@тег» и найденным в поиске фрагментом. */
fun styledMessageText(text: String, highlight: String?): AnnotatedString = buildAnnotatedString {
    append(text)
    MentionPattern.findAll(text).forEach { m ->
        addStyle(SpanStyle(color = T.Green, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
    }
    val q = highlight?.trim().orEmpty()
    if (q.isNotEmpty()) {
        var from = 0
        while (true) {
            val i = text.indexOf(q, from, ignoreCase = true)
            if (i < 0) break
            addStyle(SpanStyle(background = SearchHighlight), i, i + q.length)
            from = i + q.length
        }
    }
}

/** Максимальная ширина пузыря (iOS MessageBubble.maxWidth). */
val MessageBubbleMaxWidth = 302.dp

private val Secondary = Color.White.copy(alpha = 0.6f)

/**
 * message/Сообщение из макета (Figma 337:27570, 337:27969, 337:34166) — порт
 * iOS `Components/MessageBubble.swift`: стекло со скруглением 24, у последнего
 * сообщения серии «хвостовой» угол 0 (у своих — нижний правый, у чужих —
 * нижний левый); свои — заливка «active», чужие — «Second color» 20 %.
 * Отступы 12, между блоками 4. Имя SemiBold 12 и роль 12 белый 60 %, цитата
 * ответа — плашка белый 20 % с линией слева, текст 14, время 10 белый 60 % и
 * отметка прочтения.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    /** Последнее сообщение в серии одного автора — с «хвостом». */
    isTail: Boolean = true,
    /** Показывать имя и роль (первое чужое сообщение серии в групповом чате). */
    showHeader: Boolean = true,
    isPlaying: Boolean = false,
    /** Прогресс проигрывания голосового, 0…1 — для заполнения волны. */
    voiceProgress: Float = 0f,
    onPlayVoice: (() -> Unit)? = null,
    onOpenFile: (() -> Unit)? = null,
    onOpenImage: (() -> Unit)? = null,
    /** Долгое нажатие на вложение (картинка/файл) — то же меню, что и на пузыре. */
    onLongPress: (() -> Unit)? = null,
    /** Тап по реакции под сообщением: своя — снять, чужая — поставить такую же. */
    onToggleReaction: ((String) -> Unit)? = null,
    /** Поиск по чату: совпадения в тексте подсвечиваются. */
    highlight: String? = null,
) {
    val r = 24.dp
    val shape = RoundedCornerShape(
        topStart = r,
        topEnd = r,
        bottomEnd = if (message.mine && isTail) 0.dp else r,
        bottomStart = if (!message.mine && isTail) 0.dp else r,
    )
    val hasVoice = !message.voiceUrl.isNullOrEmpty()

    Column(
        modifier = modifier
            .widthIn(max = MessageBubbleMaxWidth)
            .width(IntrinsicSize.Max)
            // Пузырь плавно меняет размер и форму, когда появляются или пропадают реакции.
            .animateContentSize(spring(dampingRatio = 0.86f, stiffness = 380f))
            .liquidGlass(shape, if (message.mine) Fills.Active else Fills.Glass)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showHeader && !message.mine && !message.author.isNullOrEmpty() && !hasVoice) {
            BubbleHeader(message.author, message.role)
        }
        if (message.isPrivate && !message.privateLabel.isNullOrEmpty()) {
            Text(message.privateLabel, style = AppFont.sans(T.FsBase, FontWeight.SemiBold))
        }
        if (!message.replyText.isNullOrEmpty() || !message.replyAuthor.isNullOrEmpty()) {
            ReplyQuote(message.replyAuthor ?: "", message.replyText ?: "")
        }
        val gallery: List<Any> = when {
            message.uploading -> message.localImageUris
            message.imageUrls.size >= 2 -> message.imageUrls.mapNotNull { fileUrl(it) }
            else -> emptyList()
        }
        if (gallery.isNotEmpty()) {
            PhotoAlbum(
                models = gallery,
                uploading = message.uploading,
                onOpen = { onOpenImage?.invoke() },
                onLongPress = onLongPress,
            )
        } else if (!message.imageUrl.isNullOrEmpty()) {
            val w = MessageBubbleMaxWidth - 24.dp
            val iw = message.imageWidth
            val ih = message.imageHeight
            val h = if (iw != null && ih != null && iw > 0 && ih > 0) {
                min(320f, w.value * ih.toFloat() / iw.toFloat()).dp
            } else {
                180.dp
            }
            PhotoTile(
                model = fileUrl(message.imageUrl) ?: message.imageUrl,
                uploading = false,
                modifier = Modifier.size(w, h).clip(RoundedCornerShape(16.dp)),
                onOpen = { onOpenImage?.invoke() },
                onLongPress = onLongPress,
            )
        }
        if (!message.fileName.isNullOrEmpty() || !message.fileUrl.isNullOrEmpty()) {
            FileRow(message, onOpenFile, onLongPress)
        }
        if (hasVoice) {
            VoiceRow(message, showHeader, isPlaying, voiceProgress, onPlayVoice)
        }
        if (!message.text.isNullOrEmpty()) {
            if (message.isPrivate) {
                // Сообщение автору (Figma «Чат виды Сообщение автору»): текст в кавычках
                // на тёмной плашке внутри пузыря.
                Text(
                    styledMessageText("\u201C${message.text}\u201D", highlight),
                    style = AppFont.sans(T.FsMd),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            } else {
                Text(styledMessageText(message.text, highlight), style = AppFont.sans(T.FsBase))
            }
        }
        Footer(message, onToggleReaction)
    }
}

/** Альбом фото: одно фото — во всю ширину пузыря, два и больше — сетка 2 колонки с квадратами. */
@Composable
private fun PhotoAlbum(models: List<Any>, uploading: Boolean, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
    val w = MessageBubbleMaxWidth - 24.dp
    if (models.size == 1) {
        PhotoTile(
            model = models[0],
            uploading = uploading,
            modifier = Modifier.size(w, 180.dp).clip(RoundedCornerShape(16.dp)),
            onOpen = onOpen,
            onLongPress = onLongPress,
        )
        return
    }
    val gap = 4.dp
    val cell = (w - gap) / 2
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        models.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEach { model ->
                    PhotoTile(
                        model = model,
                        uploading = uploading,
                        modifier = Modifier.size(cell).clip(RoundedCornerShape(12.dp)),
                        onOpen = onOpen,
                        onLongPress = onLongPress,
                    )
                }
            }
        }
    }
}

/** Одно фото; пока uploading, поверх превью крутится спиннер и нажатие отключено. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    model: Any,
    uploading: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.1f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!uploading) onOpen() },
                onLongClick = onLongPress,
            ),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (uploading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun BubbleHeader(author: String, role: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            author,
            style = AppFont.sans(T.FsSm, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        if (!role.isNullOrEmpty()) {
            Text(role, style = AppFont.sans(T.FsSm, color = Secondary), maxLines = 1)
        }
    }
}

/** Цитата ответа: плашка белый 20 % (скругление справа 8), линия 1 dp слева. */
@Composable
fun ReplyQuote(author: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White))
        Column(modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
            Text(author, style = AppFont.sans(T.FsSm, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text, style = AppFont.sans(T.FsSm, color = Secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(message: ChatMessage, onOpenFile: (() -> Unit)?, onLongPress: (() -> Unit)?) {
    val ext = message.fileExt.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 220.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpenFile?.invoke() },
                onLongClick = onLongPress,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp, 36.dp).background(Color.White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ext.isEmpty()) "file" else ext.lowercase(),
                style = AppFont.sans(T.FsSm, FontWeight.SemiBold, color = hex("#1a1b1d")),
                maxLines = 1,
                softWrap = false,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                message.fileName ?: "Файл",
                style = AppFont.sans(T.FsSm, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(message.fileSize.orEmpty(), style = AppFont.sans(T.FsSm, color = Secondary), maxLines = 1)
        }
        AppIcon(AppIcons.downloadCloud, size = 24.dp)
    }
}

/** Голосовое: зелёный круг 42 с «play», имя/роль, волна 13 dp и длительность 8. */
@Composable
private fun VoiceRow(
    message: ChatMessage,
    showHeader: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onPlay: (() -> Unit)?,
) {
    val seed = if (message.id == 0L) 1.0 else message.id.toDouble()
    val bars = remember(message.id) {
        (0 until 44).map { i ->
            val v = abs(sin(seed * 7.3 + i * 1.7))
            2 + (v * 11).roundToInt()
        }
    }
    val animated by animateFloatAsState(progress, tween(80), label = "voice")
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(T.Green, CircleShape)
                .pressable(pressAlpha = 1f) { onPlay?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(4.dp, 16.dp).background(Color.White, CapsuleShape))
                    Box(Modifier.size(4.dp, 16.dp).background(Color.White, CapsuleShape))
                }
            } else {
                AppIcon(AppIcons.play, size = 18.dp, tint = Color.White, modifier = Modifier.offset(x = 2.dp))
            }
        }
        Column(
            modifier = Modifier.padding(top = if (message.mine) 7.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showHeader && !message.mine && !message.author.isNullOrEmpty()) {
                BubbleHeader(message.author, message.role)
            }
            Row(
                modifier = Modifier.height(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                bars.forEachIndexed { idx, h ->
                    val frac = idx.toFloat() / (bars.size - 1).coerceAtLeast(1)
                    val played = isPlaying && frac <= animated
                    Box(
                        Modifier
                            .size(2.dp, h.dp)
                            .background(Color.White.copy(alpha = if (played || !isPlaying) 1f else 0.4f), CapsuleShape),
                    )
                }
            }
            Text(message.voiceDuration ?: "0:00", style = AppFont.sans(8.sp, color = Secondary))
        }
    }
}

/**
 * Нижняя строка пузыря: реакции переносятся по строкам и не сжимаются; время и
 * отметка — справа на последней строке, если помещаются, иначе отдельной строкой.
 */
@Composable
private fun Footer(message: ChatMessage, onToggleReaction: ((String) -> Unit)?) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            message.reactions.forEach { r ->
                key(r.emoji) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 26.dp)
                            .background(if (r.mine) T.Green.copy(alpha = 0.55f) else hex("#404040"), CapsuleShape)
                            .then(
                                if (r.mine) Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), CapsuleShape) else Modifier,
                            )
                            // Своя реакция выделена; тап по ней снимает её, по чужой — ставит такую же.
                            .then(if (onToggleReaction != null) Modifier.pressable { onToggleReaction(r.emoji) } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(r.emoji, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        Text(r.count.toString(), style = AppFont.sans(T.FsSm), maxLines = 1, softWrap = false)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (message.pinned) {
                    AppIcon(AppIcons.pinSmall, size = 10.dp, tint = Secondary, modifier = Modifier.padding(bottom = 2.dp))
                }
                if (!message.time.isNullOrEmpty()) {
                    Text(message.time, style = AppFont.sans(10.sp, color = Secondary))
                }
                if (message.mine) {
                    ReadMark(read = message.read, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        },
    ) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val metaGap = 8.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        val meta = placeables.last()
        val chips = placeables.dropLast(1)
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE

        // Строки реакций: (индексы, ширина, высота).
        val lines = mutableListOf(Triple(mutableListOf<Int>(), 0, 0))
        chips.forEachIndexed { i, p ->
            var (items, w, h) = lines.last()
            val start = if (items.isEmpty()) 0 else w + gap
            if (items.isNotEmpty() && start + p.width > maxW) {
                lines.add(Triple(mutableListOf(), 0, 0))
                items = lines.last().first; w = 0; h = 0
            }
            items.add(i)
            val nw = (if (items.size == 1) 0 else w + gap) + p.width
            lines[lines.size - 1] = Triple(items, nw, max(h, p.height))
        }
        val last = lines.last()
        val metaOnLast = last.first.isEmpty() || last.second.toLong() + metaGap + meta.width <= maxW
        if (metaOnLast) {
            lines[lines.size - 1] = Triple(last.first, last.second, max(last.third, meta.height))
        } else {
            lines.add(Triple(mutableListOf(), meta.width, meta.height))
        }
        var natural = lines.maxOf { it.second }
        if (metaOnLast) natural = max(natural, if (last.first.isEmpty()) meta.width else last.second + metaGap + meta.width)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else natural
        val height = lines.sumOf { it.third } + gap * (lines.size - 1)
        layout(width, height) {
            var y = 0
            lines.forEachIndexed { n, (items, _, h) ->
                var x = 0
                items.forEach { i ->
                    val p = chips[i]
                    p.placeRelative(x, y + h - p.height)
                    x += p.width + gap
                }
                if (n == lines.size - 1) meta.placeRelative(width - meta.width, y + h - meta.height)
                y += h + gap
            }
        }
    }
}

/**
 * Отметка доставки (Figma «Сообщение/Union»): не прочитано — один кружок 8 dp;
 * прочитано — к нему добавляется второй (контур), как в макете.
 */
@Composable
fun ReadMark(read: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val second by animateFloatAsState(if (read) 1f else 0f, tween(250), label = "read")
    Canvas(modifier = modifier.size(11.dp, 8.dp)) {
        val px = size.height / 8f
        if (second > 0f) {
            // Контурный кружок справа (диаметр 7, левый край на 3.5) проявляется при прочтении.
            drawCircle(
                color.copy(alpha = color.alpha * second),
                radius = 3.5f * px * (0.4f + 0.6f * second),
                center = Offset(7f * px, 4f * px),
                style = Stroke(width = 1f * px),
            )
        }
        drawCircle(color, radius = 4f * px, center = Offset(4f * px, 4f * px))
    }
}
