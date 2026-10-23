package app.karta.likvidnosti.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T

/**
 * Модальное подтверждение (iOS ConfirmDialog, попап «Удалить отчет?»
 * Figma 349:12705): стекло «чёрный 2», радиус 24, ширина 243; заголовок
 * Bold 16 и текст 14 по левому краю, красная «Удалить» и стеклянная
 * «Отмена» по 48; фон затемняется (и размывается, если доступна подложка).
 * Размещается последним в Box экрана.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConfirmDialog(
    open: Boolean,
    title: String,
    text: String = "",
    confirmLabel: String = "Удалить",
    cancelLabel: String = "Отмена",
    busy: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(enabled = open) { onCancel() }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(800, easing = EaseInOutBack)),
        exit = fadeOut(tween(800, easing = EaseInOutBack)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlur(6.dp)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pressable(pressAlpha = 1f) { onCancel() },
            )
            run {
                Column(
                    modifier = Modifier
                        .animateEnterExit(
                            enter = scaleIn(initialScale = 0.92f),
                            exit = scaleOut(targetScale = 0.92f),
                        )
                        .width(243.dp)
                        .liquidGlass(T.ShapeCard, Fills.Black2)
                        .pressable(pressAlpha = 1f) {}
                        .padding(16.dp),
                ) {
                    Text(title, style = AppFont.sans(T.FsMd, FontWeight.Bold))
                    if (text.isNotEmpty()) {
                        Text(text, style = AppFont.sans(T.FsBase), modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppButton(confirmLabel, variant = ButtonVariant.Danger, loading = busy) { onConfirm() }
                        AppButton(cancelLabel, variant = ButtonVariant.Secondary) { onCancel() }
                    }
                }
            }
        }
    }
}
