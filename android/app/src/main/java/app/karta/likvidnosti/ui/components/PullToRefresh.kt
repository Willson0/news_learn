package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Насколько индикатор уходит вслед за пальцем (половина пути пальца). */
private const val DRAG_FACTOR = 0.5f

/**
 * Тянуть вниз, чтобы обновить содержимое (аналог PullToRefreshBox из Material3,
 * которого нет в текущей версии библиотеки).
 *
 * Работает через NestedScroll в фазе pre-scroll: жест забирается до прокрутки,
 * поэтому overscroll-эффект не мешает. Тянуть можно только когда [scrollState]
 * стоит в самом верху. [content] должен прокручиваться через `verticalScroll(scrollState)`.
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
    val indicatorPx = with(density) { 36.dp.toPx() }
    val pull = remember { mutableFloatStateOf(0f) }
    val animator = remember { Animatable(0f) }
    val refreshing by rememberUpdatedState(isRefreshing)
    val refreshAction by rememberUpdatedState(onRefresh)

    // Обновление закончилось — плавно убираем индикатор.
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pull.floatValue > 0f) {
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
        if (pull.floatValue > 0f || refreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (pull.floatValue - indicatorPx).roundToInt()) }
                    .size(36.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}
