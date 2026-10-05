package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T

enum class ButtonVariant { Accent, Soft, Secondary, Danger }

@Composable
fun AppButton(
    text: String,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Accent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = T.ShapeInput
    val base = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(shape)
    val bg = when (variant) {
        ButtonVariant.Accent -> base.background(T.GradientAccent)
        ButtonVariant.Soft -> base.background(T.GradientAccentSoft)
        ButtonVariant.Secondary -> base.background(T.SurfaceMuted)
        ButtonVariant.Danger -> base.background(T.Danger)
    }
    Box(
        modifier = modifier
            .then(bg)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = T.Text, fontSize = T.FsBase)
    }
}

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
) {
    var revealed by remember { mutableStateOf(false) }
    val textColor = if (error) T.Error else T.Text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(T.ShapeInput)
            .background(T.SurfaceMuted)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = if (error) T.Error else T.TextMuted, fontSize = T.FsBase)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = T.FsBase),
                cursorBrush = SolidColor(T.Text),
                visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isPassword) {
            Spacer(Modifier.width(8.dp))
            AppIcon(
                res = if (revealed) AppIcons.Eye else AppIcons.EyeClosed,
                size = 24.dp,
                modifier = Modifier.clickable { revealed = !revealed },
            )
        }
    }
}

@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val offset by animateDpAsState(if (checked) 29.dp else 3.dp, label = "toggle")
    val track = if (checked) Modifier.background(T.GradientAccent) else Modifier.background(T.Surface2)
    Box(
        modifier = modifier
            .width(56.dp)
            .height(30.dp)
            .clip(CircleShape)
            .then(track)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset, top = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else T.GreenKnob),
        )
    }
}

@Composable
fun InstrumentChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = Modifier
        .height(40.dp)
        .clip(T.ShapeChip)
    val styled = if (checked) base.background(T.GradientAccent)
    else base.border(BorderStroke(1.dp, Color(0x1FFFFFFF)), T.ShapeChip)
    Row(
        modifier = modifier
            .then(styled)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = T.Text, fontSize = T.FsBase)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .border(
                    BorderStroke(1.5.dp, if (checked) Color(0xE6FFFFFF) else Color(0x99FFFFFF)),
                    RoundedCornerShape(5.dp),
                )
                .background(if (checked) Color(0x40000000) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) AppIcon(AppIcons.CheckSquare, size = 12.dp, tint = Color.White)
        }
    }
}

@Composable
fun SegmentedControl(
    value: String,
    options: List<Pair<String, String>>, // value to label
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(T.ShapeCard)
            .background(T.SurfaceMuted)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (v, label) ->
            val active = v == value
            val base = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(T.ShapeChip)
            Box(
                modifier = (if (active) base.background(T.GradientAccentPurple) else base)
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = T.Text, fontSize = T.FsBase)
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = T.Text,
        fontSize = T.FsTitle,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Круглая кнопка «Назад» для вторичных экранов (в вебе её рисовал Telegram). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(T.SurfaceMuted)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.Back, size = 22.dp)
    }
}

@Composable
fun FieldError(text: String?, modifier: Modifier = Modifier) {
    if (!text.isNullOrEmpty()) {
        Text(text, modifier = modifier, color = T.Error, fontSize = T.FsSm)
    }
}
