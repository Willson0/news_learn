package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
    val replyAuthor: String? = null,
    val replyText: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val pinned: Boolean = false,
    val toAuthor: Boolean = false,
)

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
            .liquidGlass(shape, if (message.mine) Fills.Active else Fills.Glass)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showHeader && !message.mine && !message.author.isNullOrEmpty() && !hasVoice) {
            BubbleHeader(message.author, message.role)
        }
        if (message.toAuthor) {
            Text("Автору", style = AppFont.sans(T.FsSm, FontWeight.SemiBold))
        }
        if (!message.replyText.isNullOrEmpty() || !message.replyAuthor.isNullOrEmpty()) {
            ReplyQuote(message.replyAuthor ?: "", message.replyText ?: "")
        }
        if (!message.imageUrl.isNullOrEmpty()) {
            val w = MessageBubbleMaxWidth - 24.dp
            val iw = message.imageWidth
            val ih = message.imageHeight
            val h = if (iw != null && ih != null && iw > 0 && ih > 0) {
                min(320f, w.value * ih.toFloat() / iw.toFloat()).dp
            } else {
                180.dp
            }
            Box(
                modifier = Modifier
                    .size(w, h)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenImage?.invoke() },
                        onLongClick = onLongPress,
                    ),
            ) {
                AsyncImage(
                    model = fileUrl(message.imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(w, h),
                )
            }
        }
        if (!message.fileName.isNullOrEmpty() || !message.fileUrl.isNullOrEmpty()) {
            FileRow(message, onOpenFile, onLongPress)
        }
        if (hasVoice) {
            VoiceRow(message, showHeader, isPlaying, voiceProgress, onPlayVoice)
        }
        if (!message.text.isNullOrEmpty()) {
            Text(message.text, style = AppFont.sans(T.FsBase))
        }
        Footer(message)
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

@Composable
private fun Footer(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (message.reactions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                message.reactions.forEach { r ->
                    Row(
                        modifier = Modifier
                            .height(24.dp)
                            .background(hex("#404040"), CapsuleShape)
                            .then(
                                if (r.mine) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), CapsuleShape) else Modifier,
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(r.emoji, fontSize = 13.sp)
                        Text(r.count.toString(), style = AppFont.sans(T.FsSm))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
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
}

/**
 * «Сообщение/Union» из макета: два кружка 8 dp — прочитано (второй залит),
 * доставлено (только контур).
 */
@Composable
fun ReadMark(read: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier.size(11.dp, 8.dp)) {
        val px = size.height / 8f
        val stroke = Stroke(width = 1f * px)
        // Контурный кружок справа (диаметр 7, левый край на 3.5).
        drawCircle(color, radius = 3.5f * px, center = Offset(7f * px, 4f * px), style = stroke)
        if (read) {
            drawCircle(color, radius = 4f * px, center = Offset(4f * px, 4f * px))
        } else {
            drawCircle(color, radius = 3.5f * px, center = Offset(4f * px, 4f * px), style = stroke)
        }
    }
}
