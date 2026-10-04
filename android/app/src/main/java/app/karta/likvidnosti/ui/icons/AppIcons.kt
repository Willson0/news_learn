package app.karta.likvidnosti.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.R
import app.karta.likvidnosti.ui.theme.T

/** Ссылки на drawable-иконки (перенос из frontend/src/components/icons). */
object AppIcons {
    @DrawableRes val Analytics = R.drawable.ic_analytics
    @DrawableRes val Back = R.drawable.ic_back
    @DrawableRes val Bell = R.drawable.ic_bell
    @DrawableRes val Calendar = R.drawable.ic_calendar
    @DrawableRes val ChartBadge = R.drawable.ic_chart_badge
    @DrawableRes val Chat = R.drawable.ic_chat
    @DrawableRes val CheckSquare = R.drawable.ic_check_square
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val Community = R.drawable.ic_community
    @DrawableRes val Contact = R.drawable.ic_contact
    @DrawableRes val Copy = R.drawable.ic_copy
    @DrawableRes val Doc = R.drawable.ic_doc
    @DrawableRes val DownloadCloud = R.drawable.ic_download_cloud
    @DrawableRes val Eye = R.drawable.ic_eye
    @DrawableRes val EyeClosed = R.drawable.ic_eye_closed
    @DrawableRes val Filter = R.drawable.ic_filter
    @DrawableRes val Forward = R.drawable.ic_forward
    @DrawableRes val Gear = R.drawable.ic_gear
    @DrawableRes val Home = R.drawable.ic_home
    @DrawableRes val Leave = R.drawable.ic_leave
    @DrawableRes val Link = R.drawable.ic_link
    @DrawableRes val Mic = R.drawable.ic_mic
    @DrawableRes val Pencil = R.drawable.ic_pencil
    @DrawableRes val Pin = R.drawable.ic_pin
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val Plus = R.drawable.ic_plus
    @DrawableRes val Profile = R.drawable.ic_profile
    @DrawableRes val Reply = R.drawable.ic_reply
    @DrawableRes val Report = R.drawable.ic_report
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val Sort = R.drawable.ic_sort
    @DrawableRes val Trash = R.drawable.ic_trash
    @DrawableRes val TrashX = R.drawable.ic_trashx

    /** Иконка материала по ключу инструмента (перенос из materialIcon.js). */
    @DrawableRes
    fun material(key: String?): Int = when (key) {
        "gold", "silver", "platinum" -> R.drawable.ic_mat_gold
        "wti", "brent" -> R.drawable.ic_mat_oil
        "usd" -> R.drawable.ic_mat_usd
        "eur", "eurusd" -> R.drawable.ic_mat_eur
        "gas" -> R.drawable.ic_mat_gas
        "btc" -> R.drawable.ic_mat_btc
        else -> R.drawable.ic_mat_oil
    }
}

/** Монохромная иконка, перекрашиваемая в tint. */
@Composable
fun AppIcon(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = T.Text,
) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** Многоцветная иконка (например, материалы) — без перекраски. */
@Composable
fun AppImage(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
