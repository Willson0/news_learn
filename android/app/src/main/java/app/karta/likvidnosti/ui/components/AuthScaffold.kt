package app.karta.likvidnosti.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop

/** Фоновый градиент экранов авторизации (старое имя; = Theme.background). */
fun Modifier.authBackground(): Modifier = this.background(
    Brush.verticalGradient(listOf(Color(0xFF1A1B1D), Color(0xFF1E1F21), Color(0xFF161719))),
)

@Composable
fun insetTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun insetBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Общий каркас экранов авторизации (iOS AuthScaffold): стеклянная «назад» на
 * y = 78, заголовок Kreadon 24 по центру на y = [contentTop] (234), подзаголовок
 * 12 белый 60 %, поля с отступом 24 под текстом; кнопки прижаты к низу (45).
 */
@Composable
fun AuthScaffold(
    title: String? = null,
    subtitle: String? = null,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    contentTop: Dp = 234.dp,
    buttons: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().screenBackground()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = T.ScreenX)
                    .padding(top = designTop(contentTop), bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (title != null) {
                    Text(title, style = AppFont.heading(T.FsTitle), textAlign = TextAlign.Center)
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = AppFont.sans(T.FsSm, color = Color.White.copy(alpha = 0.6f)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp).padding(top = 12.dp),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = if (title == null) 0.dp else 24.dp),
                    content = content,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.ScreenX)
                    .padding(top = 12.dp, bottom = designBottom(T.NavBarBottom)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = buttons,
            )
        }
        if (showBack) {
            GlassBackButton(
                modifier = Modifier.padding(start = T.ScreenX, top = designTop(T.TopBarY)),
                onClick = onBack,
            )
        }
    }
}

/** Текст ошибки под полем: 12, красный 70 % (iOS AuthFieldError, 349:9982). */
@Composable
fun AuthFieldError(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppFont.sans(T.FsSm, color = T.Error.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Поле с ошибкой под ним (отступ 4) — iOS AuthField. */
@Composable
fun AuthField(error: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(300, easing = EaseOutFigma)),
            exit = fadeOut(tween(300, easing = EaseOutFigma)),
        ) {
            AuthFieldError(error ?: "")
        }
    }
}

/** Экран-сообщение по центру («Приветствуем, …!», «Вы успешно сменили пароль»). */
@Composable
fun AuthMessageView(text: String, showBack: Boolean = false, onBack: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize().screenBackground(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = AppFont.heading(T.FsTitle),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = T.ScreenX),
        )
        if (showBack) {
            GlassBackButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = T.ScreenX, top = designTop(T.TopBarY)),
                onClick = onBack,
            )
        }
    }
}
