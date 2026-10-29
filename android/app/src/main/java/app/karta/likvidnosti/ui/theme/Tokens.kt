package app.karta.likvidnosti.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Цвет из hex-строки «#RRGGBB» или «#RRGGBBAA» (как Color(hex:) в iOS). */
fun hex(value: String): Color {
    val s = value.trim().removePrefix("#")
    val v = s.toLong(16)
    return if (s.length == 8) {
        Color(
            red = ((v shr 24) and 0xFF).toInt(),
            green = ((v shr 16) and 0xFF).toInt(),
            blue = ((v shr 8) and 0xFF).toInt(),
            alpha = (v and 0xFF).toInt(),
        )
    } else {
        Color(
            red = ((v shr 16) and 0xFF).toInt(),
            green = ((v shr 8) and 0xFF).toInt(),
            blue = (v and 0xFF).toInt(),
        )
    }
}

/**
 * Дизайн-токены «Карта Ликвидности» — 1:1 с iOS `Theme/Theme.swift`
 * (стили Figma, страница «Дизайн» / «дизайн система / uikit»).
 * Тёмная тема, макет 393×852 (статус-бар макета — 54).
 *
 * Имена в стиле Kotlin (PascalCase) соответствуют iOS-именам:
 * `Theme.surface` → `T.Surface`, `Theme.textSecondary` → `T.TextSecondary` и т.д.
 */
object T {
    // MARK: - Фон экрана («Фон» — вертикальный градиент)
    val BgTop = hex("#1a1b1d")
    val BgMid = hex("#1e1f21")
    val BgBottom = hex("#161719")
    val Bg = BgTop

    /** Theme.background — градиент экрана. */
    val Background: Brush
        get() = Brush.verticalGradient(
            0.0f to BgTop,
            0.476f to BgMid,
            1.0f to BgBottom,
        )

    // MARK: - Поверхности
    /** «фон плашки» — карточки и блоки. */
    val Surface = hex("#25262a")
    /** «Фон плашки 2». */
    val Surface2Plate = hex("#2d2e35")
    /** «Second color» (#313131) — сплошной. */
    val Surface2 = hex("#313131")
    /** «Second color» 20 % — заливка стеклянных элементов. */
    val SurfaceMuted = hex("#313131").copy(alpha = 0.2f)
    val GlassFill = hex("#313131").copy(alpha = 0.2f)
    val NavbarBg = hex("#161616").copy(alpha = 0.2f)
    val Badge = hex("#555555")
    /** «черный 2» (#000, 40 %). */
    val Black2 = Color.Black.copy(alpha = 0.4f)

    // MARK: - Текст
    val Text = Color.White
    /** «белый неактив» (65 %). */
    val TextSecondary = Color.White.copy(alpha = 0.65f)
    /** «Белый неактив 2». Для текста поднят с 40 до 60 %: на #25262A это ≈ 4,6:1 (WCAG AA). */
    val TextMuted = Color.White.copy(alpha = 0.6f)
    /** Неактивные иконки (навбар): 40 % — для крупных значков достаточно 3:1. */
    val IconMuted = Color.White.copy(alpha = 0.4f)
    /** Подписи «Сегодня» / превью чатов — белый 60 %. */
    val Caption = Color.White.copy(alpha = 0.6f)
    val Black = Color.Black

    // MARK: - Акценты
    /** «red 100». */
    val Error = hex("#ff2a2a")
    /** «red» (#CF0E12, 54 %) — кнопка «Выйти из аккаунта», «Удалить». */
    val Danger = hex("#cf0e12").copy(alpha = 0.54f)
    /** Variable «Color» — зелёный статус «Активна». */
    val Green = hex("#629f00")
    val GreenSend = hex("#4e8f10")
    /** Кружок выключенного переключателя (тот же зелёный макета). */
    val GreenKnob = Green

    // MARK: - Градиенты (простые; составные заливки — в Fills)
    /** «active» мягкий (горизонтальный слой). */
    val GradientAccentSoft: Brush
        get() = Brush.horizontalGradient(
            0.0f to hex("#ffd900").copy(alpha = 0.2f),
            1.0f to hex("#1c4a00").copy(alpha = 0.2f),
        )

    /** Плотный вариант «active» (оба слоя поверх стекла). */
    val GradientAccent: Brush
        get() = Brush.horizontalGradient(
            0.0f to hex("#6b6a1c"),
            0.55f to hex("#3f5a18"),
            1.0f to hex("#1f4a12"),
        )

    /** «акцент 3» — пурпурный. */
    val GradientAccentPurple: Brush
        get() = Brush.horizontalGradient(
            0.304f to hex("#572546").copy(alpha = 0.6f),
            1.0f to hex("#bd5197").copy(alpha = 0.6f),
        )

    /** Насыщенный пурпур для кнопок-пилюль. */
    val GradientPurpleSolid: Brush
        get() = Brush.horizontalGradient(listOf(hex("#572546"), hex("#bd5197")))

    /** Розово-малиновый (бейдж «Обсудить»). */
    val GradientPink: Brush
        get() = Brush.linearGradient(listOf(hex("#da517d"), hex("#ff0052")))

    /** Совместимость со старыми экранами. */
    val GradientPurpleBtn: Brush get() = GradientPurpleSolid

    /** «затемнениние» — растушёвка под нижним меню. */
    val BottomFade: Brush
        get() = Brush.verticalGradient(listOf(hex("#1a1b1d").copy(alpha = 0f), hex("#1a1b1d")))

    /** «Голографическая фольга» (HoloFoil) — приближение. */
    val Holo: Brush
        get() = Brush.linearGradient(
            0.0f to hex("#c9c6d6"),
            0.18f to hex("#a9adbd"),
            0.34f to hex("#cfc3cf"),
            0.52f to hex("#b7c2bf"),
            0.70f to hex("#c7bcc9"),
            0.86f to hex("#a7adba"),
            1.0f to hex("#c4c7d2"),
        )

    // MARK: - Типографика (размеры из макета)
    val FsTitle = 24.sp
    val FsLg = 17.sp
    val FsMd = 16.sp
    val FsBase = 14.sp
    val FsSm = 12.sp

    // MARK: - Радиусы
    val RadiusCard = 24.dp
    val RadiusChip = 20.dp
    val RadiusPill = 60.dp
    val RadiusInput = 24.dp

    val ShapeCard = RoundedCornerShape(24.dp)
    val ShapeChip = RoundedCornerShape(20.dp)
    val ShapePill = RoundedCornerShape(60.dp)
    val ShapeInput = RoundedCornerShape(24.dp)

    // MARK: - Отступы и размеры (координаты кадра 393×852)
    val ScreenX = 16.dp
    val AppWidth = 393.dp
    val AppHeight = 852.dp
    /** Высота статус-бара в макете — от неё отсчитываются y-координаты. */
    val DesignStatusBar = 54.dp
    /** Высота нижнего меню + отступ под ним. */
    val TabBarInset = 104.dp
    /** Заголовок вкладки (y = 94). */
    val TitleTop = 94.dp
    /** Верхняя панель внутреннего экрана (y = 78). */
    val TopBarY = 78.dp
    /** Запас снизу под навбар. */
    val NavBarClearance = 130.dp
    /** Отступ навбара от низа экрана (вместе с home-индикатором 34). */
    val NavBarBottom = 45.dp
}
