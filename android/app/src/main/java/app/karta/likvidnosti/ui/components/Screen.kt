package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.T

/**
 * Базовый каркас экрана: фон, боковые отступы, учёт системных инсетов.
 * [scroll] включает вертикальный скролл. [bottomInset] добавляет запас снизу
 * под плавающую навигацию.
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    horizontalPadding: Dp = T.ScreenX,
    bottomInset: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val base = Modifier
        .fillMaxSize()
        .background(T.Bg)
    val col = Modifier
        .fillMaxSize()
        .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
        .padding(
            start = horizontalPadding,
            end = horizontalPadding,
            top = top + 12.dp,
            bottom = bottom + bottomInset,
        )
    Box(modifier = base.then(modifier)) {
        Column(modifier = col, verticalArrangement = verticalArrangement, content = content)
    }
}

typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

/** Шапка экрана: кнопка «назад» + заголовок (аналог screen-header). */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            BackButton(onClick = onBack)
        }
        ScreenTitle(title, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** Высота плавающей навигации + запас (для bottomInset прокручиваемых экранов). */
val NavBarInset: Dp = 110.dp
