package app.karta.likvidnosti.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.R
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.hex
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlin.math.max

// Общие элементы — порт iOS `Components/Common.swift` и `Theme.swift` (ScreenBackdrop).

/**
 * Фон экрана макета (iOS ScreenBackdrop / screenBackground()): градиент «Фон» +
 * мягкое анимированное свечение сверху (bg_glow1…5, 474/852 высоты экрана).
 */
fun Modifier.screenBackground(glow: Boolean = true): Modifier = composed {
    val painters = GlowFrames.map { painterResource(it) }
    // Свечение в макете — 393×474 в кадре 393×852: высота пропорциональна
    // экрану (и не меньше ширины × 474/393), чтобы огоньки занимали ту же долю
    // экрана на любых размерах (iOS ScreenBackdrop.glowHeight). Размер окна, а не
    // контейнера: клавиатура не сдвигает фон.
    val metrics = LocalContext.current.resources.displayMetrics
    val glowPx = remember(metrics.widthPixels, metrics.heightPixels) {
        max(metrics.widthPixels * 474f / 393f, metrics.heightPixels * 474f / 852f)
    }
    // Свечение перебирает пять вариантов Figma («Frame» 498:22347): DISSOLVE, LINEAR,
    // 6 с на каждый (iOS AnimatedGlow). Фаза — от общего времени, поэтому при
    // переходах между экранами фон продолжается без скачка. Время читается только
    // в фазе рисования: кадр перерисовывается без рекомпозиции.
    val context = LocalContext.current
    val animate = remember {
        glow && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val clock = remember { mutableStateOf(SystemClock.uptimeMillis()) }
    if (animate) {
        LaunchedEffect(Unit) {
            while (true) withFrameMillis { clock.value = SystemClock.uptimeMillis() }
        }
    }
    this.drawBehind {
        drawRect(T.Background)
        if (glow) {
            val (from, to, f) = if (animate) glowPhase(clock.value) else Triple(0, 1, 0f)
            clipRect(0f, 0f, size.width, glowPx) {
                drawGlowFrame(painters[from], glowPx, 1f - f)
                if (f > 0f) drawGlowFrame(painters[to], glowPx, f)
            }
        }
    }
}

private val GlowFrames = listOf(
    R.drawable.bg_glow1, R.drawable.bg_glow2, R.drawable.bg_glow3, R.drawable.bg_glow4, R.drawable.bg_glow5,
)
private const val GlowStepMs = 6000L

/** Общая точка отсчёта свечения для всех экранов. */
private val GlowEpoch = SystemClock.uptimeMillis()

private fun glowPhase(now: Long): Triple<Int, Int, Float> {
    val t = (now - GlowEpoch).coerceAtLeast(0L)
    val from = ((t / GlowStepMs) % GlowFrames.size).toInt()
    return Triple(from, (from + 1) % GlowFrames.size, (t % GlowStepMs) / GlowStepMs.toFloat())
}

/** scaledToFill по ширине экрана и высоте 474, по центру рамки. */
private fun DrawScope.drawGlowFrame(painter: Painter, glowPx: Float, alpha: Float) {
    val iw = painter.intrinsicSize.width
    val ih = painter.intrinsicSize.height
    if (iw <= 0f || ih <= 0f) return
    val scale = max(size.width / iw, glowPx / ih)
    val dw = iw * scale
    val dh = ih * scale
    translate((size.width - dw) / 2, (glowPx - dh) / 2) {
        with(painter) { draw(Size(dw, dh), alpha = alpha) }
    }
}

/** Фон экрана только градиентом (без свечения) — iOS `Theme.background`. */
fun Modifier.plainBackground(): Modifier = this.background(T.Background)

/**
 * Мигающий блок на месте картинки, пока она грузится (iOS LoadingBlock):
 * белый 5 → 13 % и обратно — без заглушки, которая потом подменяется фото.
 */
@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading-block")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "loading-alpha",
    )
    Box(modifier.drawBehind { drawRect(Color.White.copy(alpha = alpha)) })
}

/**
 * Загрузка картинки по URL (iOS RemoteImage). Пока грузится — прозрачно или
 * мигающий блок (`loading`); если картинки нет или она не загрузилась — `fallback`.
 * Уже загруженные картинки берутся из памяти Coil сразу, без мигания.
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: Boolean = false,
    fallback: (@Composable BoxScope.() -> Unit)? = null,
) {
    if (url == null) {
        if (fallback != null) Box(modifier) { fallback() }
        return
    }
    if (!loading && fallback == null) {
        AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = modifier)
        return
    }
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(modifier) {
        when (state) {
            is AsyncImagePainter.State.Error -> fallback?.invoke(this)
            is AsyncImagePainter.State.Success -> Unit
            else -> if (loading) LoadingBlock(Modifier.matchParentSize())
        }
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
            onState = { state = it },
        )
    }
}

/** «Голографическая фольга» обложки (iOS HoloFoil) — приближение. */
@Composable
fun HoloFoil(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(T.Holo))
}

/**
 * Круглый аватар: фото по URL поверх градиента с первой буквой имени
 * (iOS AvatarCircle). Пока фото грузится или его нет — видна буква.
 */
@Composable
fun AvatarCircle(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    initial: String? = null,
    name: String? = null,
) {
    val letter = when {
        !initial.isNullOrEmpty() -> initial
        else -> name?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: ""
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(hex("#6b7350"), hex("#3f4a2c")))),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() }
        if (url != null) {
            // Пока фото грузится — мигающий круг, без буквы, которая потом подменится фото.
            RemoteImage(url, Modifier.fillMaxSize().clip(CircleShape), loading = true) {
                if (letter.isNotEmpty()) {
                    Text(letter, style = AppFont.heading(fontSize), modifier = Modifier.align(Alignment.Center))
                }
            }
        } else if (letter.isNotEmpty()) {
            Text(letter, style = AppFont.heading(fontSize))
        }
    }
}

/**
 * Обложка из макета (заливка «Rectangle 7», режим Fill) — для отчётов без
 * своей картинки (iOS ReportCoverPlaceholder).
 */
@Composable
fun ReportCoverPlaceholder(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.report_cover),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clipToBounds(),
    )
}

/**
 * Обложка отчёта. Есть картинка — пока грузится, мигающий блок (без заглушки,
 * чтобы не было подмены); заглушка из макета — только если картинки нет или
 * она не загрузилась. `loading` — данные ещё не пришли: тоже мигающий блок.
 */
@Composable
fun ReportCover(url: String?, modifier: Modifier = Modifier, loading: Boolean = false) {
    Box(modifier = modifier.clipToBounds()) {
        when {
            url != null -> RemoteImage(url, Modifier.fillMaxSize(), loading = true) {
                ReportCoverPlaceholder(Modifier.fillMaxSize())
            }
            loading -> LoadingBlock(Modifier.fillMaxSize())
            else -> ReportCoverPlaceholder(Modifier.fillMaxSize())
        }
    }
}

/** Горизонтальная линия-разделитель (белый 10 %). */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, alpha: Float = 0.1f) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = alpha)))
}
