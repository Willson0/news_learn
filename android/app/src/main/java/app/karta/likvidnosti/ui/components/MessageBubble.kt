package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** UI-модель сообщения чата (сервер + демо-поля). */
data class ChatMessage(
    val id: Long,
    val mine: Boolean,
    val author: String? = null,
    val role: String? = null,
    val text: String? = null,
    val time: String? = null,
    val read: Boolean = false,
    val voiceDuration: String? = null,
    val fileExt: String? = null,
    val fileName: String? = null,
    val fileSize: String? = null,
    val image: Boolean = false,
    val replyAuthor: String? = null,
    val replyText: String? = null,
    val reactions: List<Pair<String, Int>> = emptyList(),
    val toAuthor: Boolean = false,
)

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = if (message.mine) 6.dp else 18.dp,
        bottomStart = if (message.mine) 18.dp else 6.dp,
    )
    val bg = if (message.mine) Modifier.background(T.GradientAccent, shape)
    else Modifier.background(T.Surface, shape).border(1.dp, Color(0x0FFFFFFF), shape)

    Column(
        modifier = modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .then(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (!message.mine && !message.author.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message.author!!, color = T.Text, fontSize = T.FsBase, fontWeight = FontWeight.Bold)
                if (!message.role.isNullOrEmpty()) {
                    Text(message.role!!, color = T.TextSecondary, fontSize = T.FsSm)
                }
            }
        }

        if (message.toAuthor) {
            Text("Автору", color = T.Text, fontSize = T.FsBase, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp))
        }

        if (!message.replyText.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x2E000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(message.replyAuthor ?: "", color = T.Text, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                Text(message.replyText!!, color = T.TextSecondary, fontSize = T.FsSm, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (message.image) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .width(240.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFCFC6D6), Color(0xFFA9ADBD), Color(0xFFB7C2BF)))),
            )
        }

        if (!message.fileName.isNullOrEmpty()) {
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(message.fileExt ?: "", color = T.Bg, fontSize = T.FsSm, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(message.fileName!!, color = T.Text, fontSize = T.FsBase)
                    Text(message.fileSize ?: "", color = T.TextSecondary, fontSize = T.FsSm)
                }
                AppIcon(AppIcons.DownloadCloud, size = 22.dp)
            }
        }

        if (!message.voiceDuration.isNullOrEmpty()) {
            VoiceRow(seed = message.id, duration = message.voiceDuration!!)
        }

        if (!message.text.isNullOrEmpty()) {
            Text(message.text!!, color = T.Text, fontSize = T.FsBase, lineHeight = 19.sp)
        }

        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.reactions.forEach { (emoji, count) ->
                    Row(
                        modifier = Modifier
                            .clip(T.ShapePill)
                            .background(Color(0x40000000))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(emoji, fontSize = T.FsSm, color = T.Text)
                        Text(count.toString(), fontSize = T.FsSm, color = T.TextSecondary)
                    }
                }
            }
        }

        if (!message.time.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message.time!!, color = T.TextSecondary, fontSize = T.FsSm)
                if (message.mine) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 12.dp)
                            .clip(T.ShapePill)
                            .background(Color(0x40FFFFFF)),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(seed: Long, duration: String) {
    val bars = (0 until 26).map { i ->
        val v = abs(sin(seed * 7.3 + i * 1.7))
        (4 + (v * 14).roundToInt())
    }
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF4E8F10)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Play, size = 18.dp, tint = Color.White)
        }
        Row(
            modifier = Modifier.weight(1f).height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            bars.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x8CFFFFFF)),
                )
            }
        }
        Text(duration, color = T.TextSecondary, fontSize = T.FsSm)
    }
}
