package app.karta.likvidnosti.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.R

/**
 * Шрифты макета (iOS `AppFont`): Kreadon Demi — заголовки, Min Sans — весь
 * остальной текст.
 */
object AppFont {
    val Kreadon = FontFamily(Font(R.font.kreadon_demi, FontWeight.SemiBold))

    val MinSans = FontFamily(
        Font(R.font.min_sans_light, FontWeight.Light),
        Font(R.font.min_sans_regular, FontWeight.Normal),
        Font(R.font.min_sans_medium, FontWeight.Medium),
        Font(R.font.min_sans_semibold, FontWeight.SemiBold),
        Font(R.font.min_sans_bold, FontWeight.Bold),
    )

    /** Theme.heading(size) — Kreadon Demi. */
    fun heading(size: TextUnit = T.FsTitle, color: Color = T.Text): TextStyle =
        TextStyle(fontFamily = Kreadon, fontWeight = FontWeight.SemiBold, fontSize = size, color = color)

    /** Theme.sans(size, weight) — Min Sans. */
    fun sans(size: TextUnit = T.FsBase, weight: FontWeight = FontWeight.Normal, color: Color = T.Text): TextStyle =
        TextStyle(fontFamily = MinSans, fontWeight = weight, fontSize = size, color = color)
}

private val KartaColors = darkColorScheme(
    primary = T.Green,
    onPrimary = T.Text,
    background = T.Bg,
    onBackground = T.Text,
    surface = T.Surface,
    onSurface = T.Text,
    error = T.Error,
)

private val Body = AppFont.MinSans

private val KartaTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFont.Kreadon, fontSize = 32.sp),
    displayMedium = TextStyle(fontFamily = AppFont.Kreadon, fontSize = 28.sp),
    displaySmall = TextStyle(fontFamily = AppFont.Kreadon, fontSize = 24.sp),
    headlineLarge = TextStyle(fontFamily = AppFont.Kreadon, fontSize = 24.sp),
    headlineMedium = TextStyle(fontFamily = AppFont.Kreadon, fontSize = 20.sp),
    headlineSmall = TextStyle(fontFamily = AppFont.Kreadon, fontSize = 18.sp),
    titleLarge = TextStyle(fontFamily = AppFont.Kreadon, fontSize = T.FsTitle),
    titleMedium = TextStyle(fontFamily = Body, fontSize = T.FsMd, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Body, fontSize = T.FsBase, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = T.FsMd, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = T.FsBase, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = Body, fontSize = T.FsSm, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = Body, fontSize = T.FsBase, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Body, fontSize = T.FsSm, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun KartaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KartaColors,
        typography = KartaTypography,
        content = content,
    )
}

/** Высота системного статус-бара. */
@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** Высота системной навигации снизу. */
@Composable
fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Перевод y-координаты макета (кадр 393×852 со статус-баром 54) в отступ от
 * верха экрана Android: статус-бар устройства + (y − 54).
 * Например, `designTop(T.TopBarY)` — верх стеклянной «назад».
 */
@Composable
fun designTop(y: Dp): Dp = statusBarTop() + (y - T.DesignStatusBar)

/**
 * Отступ снизу из макета (кадр с home-индикатором 34): не меньше значения
 * макета и не меньше системной навигации + (bottom − 34).
 */
@Composable
fun designBottom(bottom: Dp): Dp {
    val nav = navigationBarBottom()
    val rel = nav + (bottom - 34.dp)
    return if (rel > bottom) rel else bottom
}

/** Запас снизу под навбар (iOS Theme.navBarClearance = 130 от низа экрана). */
@Composable
fun navBarClearance(): Dp = T.NavBarClearance + (designBottom(T.NavBarBottom) - T.NavBarBottom)
