package app.karta.likvidnosti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val KartaColors = darkColorScheme(
    primary = T.Green,
    onPrimary = T.Text,
    background = T.Bg,
    onBackground = T.Text,
    surface = T.Surface,
    onSurface = T.Text,
    error = T.Error,
)

// Заголовки — Kreadon, текст — Min Sans (платные). Файлов нет, поэтому системный
// шрифт-фолбэк (как и в вебе до появления лицензионных файлов).
private val Body = FontFamily.Default

private val KartaTypography = Typography(
    bodyLarge = TextStyle(fontFamily = Body, fontSize = T.FsMd, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = T.FsBase, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontFamily = Body, fontSize = T.FsTitle, fontWeight = FontWeight.SemiBold),
)

@Composable
fun KartaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KartaColors,
        typography = KartaTypography,
        content = content,
    )
}
