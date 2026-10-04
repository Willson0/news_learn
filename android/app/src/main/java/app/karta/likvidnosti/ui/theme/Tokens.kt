package app.karta.likvidnosti.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Дизайн-токены «Карта Ликвидности» (перенос из frontend/src/styles/tokens.css).
 * Тёмная тема, мобильный макет.
 */
object T {
    // --- Поверхности ---
    val Bg = Color(0xFF1A1B1D)
    val Surface = Color(0xFF25262A)
    val Surface2 = Color(0xFF313131)
    val SurfaceMuted = Color(0x33313131) // rgba(49,49,49,0.2)
    val NavbarBg = Color(0x33161616)     // rgba(22,22,22,0.2)
    val Badge = Color(0xFF555555)

    // --- Текст ---
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xA6FFFFFF) // 0.65
    val TextMuted = Color(0x66FFFFFF)     // 0.4
    val Black = Color(0xFF000000)

    // --- Акценты ---
    val Danger = Color(0xFF9C1F1F)
    val Error = Color(0xFFFF4D4D)
    val Green = Color(0xFF7BC043)
    val GreenKnob = Color(0xFF5BB318)

    // --- Размеры шрифта ---
    val FsTitle = 24.sp
    val FsLg = 17.sp
    val FsMd = 16.sp
    val FsBase = 14.sp
    val FsSm = 12.sp

    // --- Радиусы ---
    val RadiusCard = 24.dp
    val RadiusChip = 20.dp
    val RadiusPill = 60.dp
    val RadiusInput = 24.dp

    val ShapeCard = RoundedCornerShape(24.dp)
    val ShapeChip = RoundedCornerShape(20.dp)
    val ShapePill = RoundedCornerShape(60.dp)
    val ShapeInput = RoundedCornerShape(24.dp)

    // --- Отступы ---
    val ScreenX = 16.dp

    // --- Градиенты ---
    // Основной акцент: оливково-зелёный (кнопки, активная вкладка).
    val GradientAccent: Brush
        get() = Brush.horizontalGradient(
            0.0f to Color(0xFFC2AB1F),
            0.24f to Color(0xFF8F9A26),
            0.64f to Color(0xFF3F6A16),
            1.0f to Color(0xFF1B4A0B),
        )

    // Приглушённый акцент (мягкие плашки/кнопки на карточке).
    val GradientAccentSoft: Brush
        get() = Brush.horizontalGradient(
            0.0f to Color(0x33FFD900),
            1.0f to Color(0x331CEB00),
        )

    // Пурпурный акцент (активный чип, сегмент).
    val GradientAccentPurple: Brush
        get() = Brush.horizontalGradient(
            0.3f to Color(0x99572546),
            1.0f to Color(0x99BD5197),
        )

    // Насыщенный пурпур для кнопок-пилюль.
    val GradientPurpleSolid: Brush
        get() = Brush.horizontalGradient(
            0.0f to Color(0xFF57253F),
            1.0f to Color(0xFFBD5197),
        )

    // Пурпурная кнопка «Обсудить».
    val GradientPurpleBtn: Brush
        get() = Brush.horizontalGradient(
            0.0f to Color(0xFF3A1230),
            1.0f to Color(0xFF9C2F6E),
        )

    // «Голографическая фольга» обложки отчёта (приближение).
    val Holo: Brush
        get() = Brush.linearGradient(
            0.0f to Color(0xFFC9C6D6),
            0.18f to Color(0xFFA9ADBD),
            0.34f to Color(0xFFCFC3CF),
            0.52f to Color(0xFFB7C2BF),
            0.70f to Color(0xFFC7BCC9),
            0.86f to Color(0xFFA7ADBA),
            1.0f to Color(0xFFC4C7D2),
        )
}
