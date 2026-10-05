package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.ui.theme.T

/**
 * Модальное подтверждение (аналог admin/ConfirmDialog.vue). Размещается
 * последним в Box экрана; перекрывает контент затемнением.
 */
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
    if (!open) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x40000000))
            .clickable(enabled = !busy) { onCancel() }
            .padding(horizontal = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 276.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xEB202124))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold)
            if (text.isNotEmpty()) {
                Text(
                    text,
                    color = T.TextSecondary,
                    fontSize = T.FsBase,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(T.ShapePill)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF8C1B1B), Color(0xFF6E1212))),
                    )
                    .clickable(enabled = !busy) { onConfirm() },
                contentAlignment = Alignment.Center,
            ) {
                Text(confirmLabel, color = T.Text, fontSize = T.FsBase)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(T.ShapePill)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0x59FFFFFF)), T.ShapePill)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Text(cancelLabel, color = T.Text, fontSize = T.FsBase)
            }
        }
    }
}
