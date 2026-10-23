package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Иконка html-файла (аналог IconFileHtml.vue) — бирюзовый документ с меткой html. */
@Composable
fun FileHtmlIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF7FF5EF)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(13.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text("html", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}
