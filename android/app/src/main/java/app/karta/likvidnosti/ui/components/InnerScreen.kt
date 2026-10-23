package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop

/**
 * Внутренний экран с прокруткой и стеклянной верхней панелью — как iOS
 * `.glassTopBar()` в `MainFlow.destination`: «назад» на y = 78 (панель 42 + 8),
 * контент начинается на y = 128 + [contentPadding] (по умолчанию 32 → заголовок
 * на y = 160), отступы по бокам 16, снизу 40. Контент прокручивается под
 * панелью и размывается под её стеклом.
 */
@Composable
fun InnerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentTop: Dp = 160.dp,
    bottomPadding: Dp = 40.dp,
    spacing: Dp = 0.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackdropBox(
        modifier = modifier.fillMaxSize(),
        contentModifier = Modifier.screenBackground(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(contentTop), bottom = designBottom(bottomPadding)),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        },
        overlay = {
            GlassTopBar(onBack = onBack, modifier = Modifier.fillMaxWidth(), trailing = trailing)
            overlay()
        },
    )
}
