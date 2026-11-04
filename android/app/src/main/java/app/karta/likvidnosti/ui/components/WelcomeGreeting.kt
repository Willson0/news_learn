package app.karta.likvidnosti.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.delay
import java.util.Calendar

/** Приветствие при входе в приложение: «Доброе утро, Никита» и т. п. по местному времени. */
object Greeting {
    fun text(name: String?, now: Calendar = Calendar.getInstance()): String? {
        val first = name?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val part = when (now.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Доброе утро"
            in 12..16 -> "Добрый день"
            in 17..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        return "$part, $first"
    }
}

private const val DELAY = 0.25f   // пауза перед первой буквой, с
private const val PER_CHAR = 0.045f
private const val FADE = 0.22f    // проявление одной буквы
private const val HOLD_MS = 1300L // сколько текст стоит целиком
private const val EXIT_MS = 550   // растворение

/**
 * Полноэкранное приветствие в стиле экрана «Приветствуем, имя!» (заголовок 24 на фоне
 * приложения). Текст печатается: буквы по очереди проявляются мягким фейдом, раскладка
 * не прыгает (ещё не напечатанные буквы просто прозрачны). Потом пауза, и экран аккуратно
 * растворяется: текст чуть всплывает и размывается, фон тает, под ним — приложение.
 * Тап или «назад» закрывают сразу. 1:1 с iOS WelcomeGreetingView.
 */
@Composable
fun WelcomeGreeting(text: String, onFinish: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    var leaving by remember { mutableStateOf(false) }
    val exit by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(EXIT_MS, easing = FastOutSlowInEasing),
        label = "greeting-exit",
        finishedListener = { if (leaving) onFinish() },
    )
    LaunchedEffect(text) {
        val total = DELAY + text.length * PER_CHAR + FADE
        val t0 = withFrameNanos { it }
        while (t < total && !leaving) {
            withFrameNanos { now -> t = (now - t0) / 1_000_000_000f }
        }
        delay(HOLD_MS)
        leaving = true
    }
    BackHandler(enabled = !leaving) { leaving = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit }
            .screenBackground()
            .pointerInput(Unit) { detectTapGestures { leaving = true } }
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            typed(text, t),
            style = AppFont.heading(T.FsTitle),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = T.ScreenX)
                .graphicsLayer { translationY = -10.dp.toPx() * exit }
                .blur((6 * exit).dp),
        )
    }
}

private fun typed(text: String, t: Float): AnnotatedString = buildAnnotatedString {
    text.forEachIndexed { i, ch ->
        val p = ((t - DELAY - i * PER_CHAR) / FADE).coerceIn(0f, 1f)
        withStyle(SpanStyle(color = T.Text.copy(alpha = p))) { append(ch) }
    }
}
