package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.icons.IconDef
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.hex

// Базовые элементы управления — порт iOS `Components/Controls.swift`
// (Button/default, input/Строка ввода, Button/Switch, чипы, Button/в отчете …).
// Все они в макете стеклянные (эффект «glass»).

/** iOS AppButtonVariant: accent/soft — «active», secondary — стекло 20 %, danger — «red». */
enum class ButtonVariant { Accent, Soft, Secondary, Danger }

/** Button/default: 48, скругление 24, Min Sans 14, стекло. */
@Composable
fun AppButton(
    text: String,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Accent,
    enabled: Boolean = true,
    /** Запрос выполняется: вместо подписи крутится индикатор, нажатие не срабатывает. */
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = T.ShapeInput
    val fill = when (variant) {
        ButtonVariant.Accent, ButtonVariant.Soft -> Fills.Active
        ButtonVariant.Secondary -> Fills.Glass
        ButtonVariant.Danger -> Fills.Danger
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .liquidGlass(shape, fill)
            .pressable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text, style = AppFont.sans(T.FsBase), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * input/Строка ввода: стекло, 48, отступы 16, Min Sans 14. С [label] —
 * вариант «Строка ввода изменение чата» (64, подпись 12 сверху).
 * [isPassword] = iOS `isSecure` (глаз-переключатель справа).
 */
@Composable
fun AppInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    error: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    label: String? = null,
    height: Dp? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    val textColor = if (error) T.Error else T.Text
    // Своё состояние с курсором: если родитель переформатировал ввод (маска
    // телефона, фильтр цифр), курсор ставится в конец, а не посреди текста.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height ?: if (label == null) 48.dp else 64.dp)
            .liquidGlass(T.ShapeInput)
            .padding(start = 16.dp, end = if (isPassword) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (label != null) {
                Text(label, style = AppFont.sans(T.FsSm, color = T.TextSecondary))
            }
            BasicTextField(
                value = shown,
                onValueChange = { next ->
                    fieldValue = next
                    if (next.text != value) onValueChange(next.text)
                },
                singleLine = singleLine,
                textStyle = AppFont.sans(T.FsBase, color = textColor),
                cursorBrush = SolidColor(T.Green),
                visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword && !revealed) KeyboardType.Password else keyboardType,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = AppFont.sans(T.FsBase, color = T.TextMuted), maxLines = 1)
                        }
                        inner()
                    }
                },
            )
        }
        if (isPassword) {
            AppIcon(
                if (revealed) AppIcons.eye else AppIcons.eyeClosed,
                size = 24.dp,
                modifier = Modifier.pressable(pressAlpha = 1f) { revealed = !revealed },
            )
        }
    }
}

private val SwitchSpring = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/**
 * Button/Switch: капсула 50×30 со стеклом. Вкл — «active» и белый кружок
 * справа; выкл — стекло 20 % и зелёный кружок (#629F00) слева.
 */
@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 24.dp else 4.dp, SwitchSpring, label = "toggle")
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .liquidGlass(CapsuleShape, if (checked) Fills.Active else Fills.Glass)
            .pressable(enabled = enabled, pressAlpha = 1f) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobX, y = 4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .fillShape(GlassFill(if (checked) Color.White else T.Green)),
        )
    }
}

/**
 * Чип инструмента с чекбоксом (фильтры, уведомления, отслеживаемые):
 * скругление 12, высота 40, Min Sans 14 + чекбокс 20, стекло; вкл — «active».
 */
@Composable
fun InstrumentChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    hPadding: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .liquidGlass(shape, if (checked) Fills.Active else Fills.Glass)
            .pressable(pressAlpha = 1f, onClick = onToggle)
            .padding(horizontal = hPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = AppFont.sans(T.FsBase), maxLines = 1)
        AppIcon(if (checked) AppIcons.checkboxOn else AppIcons.checkboxOff, size = 20.dp)
    }
}

private val LensSpring = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/** Пурпурная «линза» выбранного пункта (iOS SelectionLens): стекло «акцент 3». */
@Composable
fun SelectionLens(modifier: Modifier = Modifier, radius: Dp = T.RadiusChip) {
    Box(modifier = modifier.liquidGlass(RoundedCornerShape(radius), Fills.Purple))
}

/**
 * Сегменты («Неделя / Месяц / Год», «Все чаты / Личные чаты»): подложка
 * «Фон плашки 2» (или стекло при [glass]), выбранный — пурпурная линза,
 * переезжающая с пружиной; высота 56, пункты 48.
 */
@Composable
fun SegmentedControl(
    value: String,
    options: List<Pair<String, String>>, // value to label
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    val shape = T.ShapeCard
    val container = if (glass) Modifier.liquidGlass(shape) else Modifier.fillShape(GlassFill(T.Surface2Plate), shape)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(container)
            .padding(4.dp),
    ) {
        val n = options.size.coerceAtLeast(1)
        val itemW = (maxWidth - 4.dp * (n - 1)) / n
        val index = options.indexOfFirst { it.first == value }
        val lensX by animateDpAsState((itemW + 4.dp) * index.coerceAtLeast(0), LensSpring, label = "segment")
        if (index >= 0) {
            SelectionLens(Modifier.offset(x = lensX).size(width = itemW, height = 48.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (v, label) ->
                Box(
                    modifier = Modifier
                        .width(itemW)
                        .fillMaxHeight()
                        .pressable(pressAlpha = 1f) { if (v != value) onSelect(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = AppFont.sans(T.FsBase), maxLines = 1)
                }
            }
        }
    }
}

/** Заголовок экрана / секции — Kreadon Demi 24 (iOS Theme.heading). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = T.FsTitle) {
    Text(text, modifier = modifier, style = AppFont.heading(size))
}

/** Кнопка «назад» (старое имя) — стеклянный круг 42 из макета. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassBackButton(modifier = modifier, onClick = onClick)
}

/** Текст ошибки под полем (iOS AuthFieldError). */
@Composable
fun FieldError(text: String?, modifier: Modifier = Modifier) {
    if (!text.isNullOrEmpty()) AuthFieldError(text, modifier)
}

/** Варианты ReportActionButton (iOS ReportActionButton.Variant). */
enum class ReportActionVariant { Accent, Pink, ChartOff, ChartOn }

/**
 * Button/в отчете и Button/Button chart: 48, скругление 24, текст 14 слева,
 * справа кружок 32 с иконкой 20.
 */
@Composable
fun ReportActionButton(
    label: String,
    icon: IconDef,
    modifier: Modifier = Modifier,
    variant: ReportActionVariant = ReportActionVariant.Accent,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val surface = when (variant) {
        ReportActionVariant.Accent -> Modifier.liquidGlass(shape, Fills.Active)
        ReportActionVariant.Pink -> Modifier.liquidGlass(shape, Fills.Pink)
        ReportActionVariant.ChartOn -> Modifier.liquidGlass(shape)
        ReportActionVariant.ChartOff -> Modifier.border(1.dp, Color.White, shape)
    }
    val badge = when (variant) {
        ReportActionVariant.Pink -> hex("#da517d")
        ReportActionVariant.ChartOff -> Color.White
        else -> T.Green
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(surface)
            .pressable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppFont.sans(T.FsBase), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).fillShape(GlassFill(badge)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, size = 20.dp, tint = if (variant == ReportActionVariant.ChartOff) T.Green else Color.White)
        }
    }
}

/**
 * Маленькая пилюля-действие «Искать» (Button/искать): 42, капсула,
 * Min Sans 13 с трекингом 0.52, стекло «active».
 */
@Composable
fun PillActionButton(title: String, modifier: Modifier = Modifier, height: Dp = 42.dp, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(height)
            .liquidGlass(CapsuleShape, Fills.Active)
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = AppFont.sans(13.sp).copy(letterSpacing = 0.52.sp))
    }
}

/** Пурпурная пилюля «Отвязать / Привязать / Добавить / Удалить» (32). */
@Composable
fun PurplePillButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(32.dp)
            .liquidGlass(CapsuleShape, Fills.Purple)
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = AppFont.sans(13.sp).copy(letterSpacing = 0.52.sp))
    }
}
