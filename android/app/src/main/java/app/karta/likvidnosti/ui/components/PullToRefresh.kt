package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Насколько индикатор уходит вслед за пальцем (половина пути пальца). */
private const val DRAG_FACTOR = 0.5f

/** Кружок крутится не меньше этого, даже если ответ пришёл мгновенно. */
private const val MIN_SPIN_MS = 700L

/** Подложка кружка: тёмное стекло, чтобы белая дуга читалась и на фото шапки. */
private val IndicatorFill = GlassFill(Color(0xFF313131).copy(alpha = 0.6f))

/**
 * Тянуть вниз, чтобы обновить содержимое (аналог PullToRefreshBox из Material3,
 * которого нет в текущей версии библиотеки).
 *
 * Работает через NestedScroll в фазе pre-scroll: жест забирается до прокрутки,
 * поэтому overscroll-эффект не мешает. Тянуть можно только когда [scrollState]
 * стоит в самом верху. [content] должен прокручиваться через `verticalScroll(scrollState)`.
 *
 * Экраны заходят под статус-бар, поэтому кружок (стекло 40 с белой дугой, как
 * на iOS) стоит под статус-баром: пока тянут, дуга дорисовывается, во время
 * обновления крутится. Заодно перечитывается подписка: после оплаты обновление
 * снимает режим «без подписки».
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { 72.dp.toPx() }
    val pull = remember { mutableFloatStateOf(0f) }
    val animator = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }
    val spinning = isRefreshing || holding
    val refreshing by rememberUpdatedState(spinning)
    val refreshAction by rememberUpdatedState {
        holding = true
        scope.launch { delay(MIN_SPIN_MS); holding = false }
        scope.launch { Session.loadSubscription() }
        onRefresh()
    }

    // Обновление закончилось — плавно убираем индикатор.
    LaunchedEffect(spinning) {
        if (!spinning && pull.floatValue > 0f) {
            animator.snapTo(pull.floatValue)
            animator.animateTo(0f) { pull.floatValue = value }
        }
    }

    val connection = remember(scrollState, triggerPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || refreshing) return Offset.Zero
                val current = pull.floatValue
                return when {
                    // Тянем вниз, список наверху: двигаем индикатор и забираем жест себе.
                    available.y > 0f && scrollState.value == 0 -> {
                        pull.floatValue = current + available.y * DRAG_FACTOR
                        Offset(0f, available.y)
                    }
                    // Тянем вверх, индикатор ещё открыт: сначала свернём его.
                    available.y < 0f && current > 0f -> {
                        val step = minOf(-available.y * DRAG_FACTOR, current)
                        pull.floatValue = current - step
                        Offset(0f, -step / DRAG_FACTOR)
                    }
                    else -> Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val start = pull.floatValue
                if (start > 0f) {
                    val triggered = start >= triggerPx && !refreshing
                    if (triggered) refreshAction()
                    val target = if (start >= triggerPx) triggerPx else 0f
                    animator.snapTo(start)
                    animator.animateTo(target) { pull.floatValue = value }
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(connection)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pull.floatValue.roundToInt()) },
        ) {
            content()
        }
        // Показ меняется только на границе, а не на каждом кадре вытягивания.
        val pulled by remember { derivedStateOf { pull.floatValue > 0f } }
        if (pulled || spinning) {
            PullIndicator(pull = pull, triggerPx = triggerPx, spinning = spinning)
        }
    }
}

/**
 * Стеклянный кружок под статус-баром. Читает [pull] сам: кадры вытягивания
 * пересоставляют только его, а не весь экран под ним.
 */
@Composable
private fun BoxScope.PullIndicator(pull: MutableFloatState, triggerPx: Float, spinning: Boolean) {
    val progress = (pull.floatValue / triggerPx).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = insetTop() + 6.dp)
            .offset { IntOffset(0, (minOf(pull.floatValue, triggerPx) * 0.15f).roundToInt()) }
            .graphicsLayer {
                val scale = if (spinning) 1f else 0.6f + 0.4f * progress
                scaleX = scale
                scaleY = scale
                alpha = if (spinning) 1f else (progress * 4f).coerceAtMost(1f)
            }
            .size(40.dp)
            .liquidGlass(CircleShape, IndicatorFill),
        contentAlignment = Alignment.Center,
    ) {
        if (spinning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = T.Text,
                strokeWidth = 2.5.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                progress = { 0.75f * progress },
                modifier = Modifier.size(20.dp).rotate(progress * 270f),
                color = T.Text,
                strokeWidth = 2.5.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
