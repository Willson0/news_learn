package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.T

/**
 * Бегущий блик для скелетонов-заглушек (аналог shimmer на вебе).
 * Используется, чтобы вёрстка не скакала во время загрузки списков.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-x",
    )
    return Brush.linearGradient(
        colors = listOf(Color(0x0FFFFFFF), Color(0x29FFFFFF), Color(0x0FFFFFFF)),
        start = Offset(x, 0f),
        end = Offset(x + 320f, 320f),
    )
}

/** Прямоугольная плашка-заглушка с бликом. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier = modifier.clip(shape).background(shimmerBrush()))
}

/** Скелетон строки списка чатов (совпадает по размерам с ChatRow). */
@Composable
fun ChatRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBox(modifier = Modifier.size(56.dp), shape = CircleShape)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(modifier = Modifier.width(140.dp).height(14.dp), shape = RoundedCornerShape(10.dp))
            SkeletonBox(modifier = Modifier.width(220.dp).height(12.dp), shape = RoundedCornerShape(10.dp))
        }
    }
}

/** Несколько строк-заглушек чатов под заголовком-секцией. */
@Composable
fun ChatListSkeleton(rows: Int = 6) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SkeletonBox(modifier = Modifier.width(120.dp).height(16.dp))
        Column {
            repeat(rows) { ChatRowSkeleton() }
        }
    }
}

/** Скелетон карточки отчёта (iOS ReportCardSkeleton — по форме ReportCard). */
@Composable
fun ReportCardSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(162.dp), shape = T.ShapeCard)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBox(modifier = Modifier.width(220.dp).height(16.dp), shape = RoundedCornerShape(10.dp))
            SkeletonBox(modifier = Modifier.width(90.dp).height(12.dp), shape = RoundedCornerShape(10.dp))
            SkeletonBox(modifier = Modifier.width(280.dp).height(12.dp), shape = RoundedCornerShape(10.dp))
            SkeletonBox(modifier = Modifier.width(240.dp).height(12.dp), shape = RoundedCornerShape(10.dp))
        }
        SkeletonBox(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp),
            shape = T.ShapeInput,
        )
    }
}

/** Лента карточек-заглушек. */
@Composable
fun ReportListSkeleton(cards: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(cards) { ReportCardSkeleton() }
    }
}
