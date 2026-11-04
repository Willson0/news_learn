package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Где проиграть сердечко двойного тапа: сообщение и точка внутри пузыря. */
data class HeartBurstState(val messageId: Long, val at: Offset, val token: Long = System.nanoTime())

private val HeartPath = PathParser().parsePathString(
    "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09" +
        "C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z",
).toPath()

private val HeartPink = Color(0xFFFF2D6F)
private val HeartBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFFF6FB0), Color(0xFFFF0A54)),
    start = Offset(2f, 3f),
    end = Offset(22f, 21f),
)

/**
 * Маленькое сердце в точке двойного тапа (цвета розовой части логотипа): мягко
 * «выпрыгивает» пружиной, за ним расходится тонкое кольцо, затем сердце чуть
 * всплывает и тает. Всего ~0,8 с, 26 dp — заметно, но не отвлекает от переписки.
 * Рисуется поверх пузыря и нажатия не ловит.
 */
@Composable
fun HeartBurst(burst: HeartBurstState, modifier: Modifier = Modifier) {
    val pop = remember(burst.token) { Animatable(0f) }
    val ring = remember(burst.token) { Animatable(0f) }
    val fade = remember(burst.token) { Animatable(0f) }
    LaunchedEffect(burst.token) {
        launch { ring.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f)) }
        delay(420)
        fade.animateTo(1f, tween(350, easing = FastOutLinearInEasing))
    }
    // Холст размером с пузырь (modifier = matchParentSize): рисуем вокруг точки касания,
    // выходить за края пузыря можно — Canvas не обрезает рисунок.
    Canvas(modifier) {
        val c = burst.at
        // Кольцо: из-под сердца расходится и гаснет.
        val r = ring.value
        drawCircle(
            color = HeartPink.copy(alpha = 0.8f * (1f - r)),
            radius = 15.dp.toPx() * (0.6f + 1.3f * r),
            center = c,
            style = Stroke(width = 1.5.dp.toPx()),
        )
        // Сердце: пружина с перелётом (pop > 1), потом всплывает на 16 dp и тает.
        val f = fade.value
        val p = pop.value
        val scale = if (f > 0f) 1f - 0.1f * f else 0.3f + 0.7f * p
        val alpha = p.coerceIn(0f, 1f) * (1f - f)
        if (alpha <= 0f) return@Canvas
        val heartPx = 26.dp.toPx()
        val k = heartPx / 24f * scale
        val rise = 16.dp.toPx() * f
        withTransform({
            translate(left = c.x - 12f * k, top = c.y - 12f * k - rise)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawPath(HeartPath, HeartBrush, alpha = alpha)
        }
    }
}
