@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.karta.likvidnosti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AvatarCircle
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.FlowLayout
import app.karta.likvidnosti.ui.components.GlassCircleButton
import app.karta.likvidnosti.ui.components.HairlineDivider
import app.karta.likvidnosti.ui.components.InstrumentChip
import app.karta.likvidnosti.ui.components.RemoteImage
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.TabScaffold
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.components.figmaFade
import app.karta.likvidnosti.ui.components.PullToRefreshBox
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import app.karta.likvidnosti.ui.theme.navBarClearance
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class ProfInstrument(val key: String, val label: String, val on: Boolean)

private val SORT_OPTIONS = listOf("new" to "Сначала новые", "old" to "Сначала Старые", "name" to "По названию")

/** «Сегодня» / «Вчера» и подписи в блоке подписки — белый 60 %. */
private val Caption = Color.White.copy(alpha = 0.6f)

private class HistoryGroup(val title: String, val items: List<ReportDto>)

private val DATE_FORMATS = listOf("dd.MM.yyyy", "dd.MM.yy", "yyyy-MM-dd")
private val DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun parseDate(s: String?): LocalDate? {
    if (s.isNullOrEmpty()) return null
    for (p in DATE_FORMATS) {
        val d = runCatching { LocalDate.parse(s.take(p.length), DateTimeFormatter.ofPattern(p)) }.getOrNull()
        if (d != null) return d
    }
    return null
}

private fun dayTitle(date: LocalDate?, fallback: String?): String {
    if (date == null) return fallback ?: ""
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DAY_FMT)
    }
}

/** По дате — группы «Сегодня» / «Вчера» / дата; по названию — одним списком. */
private fun historyGroups(history: List<ReportDto>, sort: String): List<HistoryGroup> {
    if (sort == "name") {
        return listOf(HistoryGroup("", history.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })))
    }
    val dated = history.map { it to parseDate(it.date) }
    val sorted = if (sort == "old") dated.sortedBy { it.second ?: LocalDate.MIN } else dated.sortedByDescending { it.second ?: LocalDate.MIN }
    val groups = mutableListOf<HistoryGroup>()
    for ((report, date) in sorted) {
        val title = dayTitle(date, report.date)
        val last = groups.lastOrNull()
        if (last != null && last.title == title) {
            groups[groups.lastIndex] = HistoryGroup(title, last.items + report)
        } else {
            groups.add(HistoryGroup(title, listOf(report)))
        }
    }
    return groups
}

/**
 * «Профиль» (iOS ProfileView, Figma 484:21226): шапка 309 — фото под размытием
 * 50 и чёрным 50 %, аватар 148 и @тег; стеклянная «настройки» справа. Ниже —
 * подписка, отслеживаемые инструменты, настройки и история с сортировкой.
 * Без подписки («Доступный экран профиля», 474:11853) — имя, тег и «Условия».
 * В навбаре справа — круглая кнопка чата с админом (задаёт AppRoot).
 */
@Composable
fun ProfileScreen(
    onSettings: () -> Unit,
    onSubscription: () -> Unit,
    onNotifications: () -> Unit,
    onAccountData: () -> Unit,
    onAdmins: () -> Unit,
    onAgreement: () -> Unit,
    onLogout: () -> Unit,
    onOpenReport: (Long) -> Unit,
    navBar: @Composable () -> Unit,
) {
    var tag by remember { mutableStateOf(Session.user?.username ?: "") }
    var avatar by remember { mutableStateOf(Session.user?.avatarUrl) }
    var subActive by remember { mutableStateOf(false) }
    var subUntil by remember { mutableStateOf("") }
    var subPrice by remember { mutableStateOf("") }
    val instruments = remember { mutableStateListOf<ProfInstrument>() }
    var history by remember { mutableStateOf(AppData.reports ?: emptyList<ReportDto>()) }
    var sortOpen by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("new") }
    val scope = rememberCoroutineScope()
    val isAdmin = Session.isAdmin
    val restricted = Session.restricted

    var refreshing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    suspend fun fetch() {
        runCatching { Repository.profile() }.getOrNull()?.let { p ->
            p.user?.let { tag = it.username ?: tag; avatar = it.avatarUrl ?: avatar }
            p.subscription?.let {
                subActive = it.active; subUntil = it.until ?: ""; subPrice = it.price ?: ""
                Session.setSubscription(it.active)
            }
            instruments.clear()
            instruments.addAll(p.instruments.map { ProfInstrument(it.key, it.label, it.on) })
        }
        runCatching { Repository.reports() }.onSuccess { history = it }
    }

    LaunchedEffect(Unit) {
        Session.user?.let { u -> tag = u.username ?: tag; avatar = u.avatarUrl ?: avatar }
        fetch()
    }

    fun saveInstruments() {
        scope.launch { runCatching { Repository.syncInstruments(instruments.filter { it.on }.map { it.key }) } }
    }

    val avatarInitial = tag.removePrefix("@").take(1).uppercase()
        .ifEmpty { (Session.user?.name ?: "").take(1).uppercase().ifEmpty { "?" } }
    val displayTag = if (tag.isEmpty()) "" else if (tag.startsWith("@")) tag else "@$tag"

    TabScaffold(navBar = navBar) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            scrollState = scrollState,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try { fetch() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = navBarClearance()),
            ) {
                Header(
                    avatarUrl = fileUrl(avatar),
                    initial = avatarInitial,
                    tag = displayTag,
                    name = Session.user?.name ?: "",
                    restricted = restricted,
                    onSettings = onSettings,
                )

                if (restricted) {
                    ConditionsBlock(subPrice, subUntil, onSubscription)
                    // Без подписки доступа к настройкам аккаунта нет: выход остаётся здесь.
                    Column(modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp)) {
                        var loggingOut by remember { mutableStateOf(false) }
                        AppButton("Выйти из аккаунта", variant = ButtonVariant.Danger, loading = loggingOut) {
                            loggingOut = true
                            scope.launch {
                                runCatching { Repository.logout() }
                                onLogout()
                            }
                        }
                    }
                } else {
                    SubscriptionBlock(
                        subActive, subUntil, subPrice, onSubscription,
                        Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp),
                    )

                    ScreenTitle(
                        "Отслеживаемые инструменты",
                        modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 40.dp).widthIn(max = 196.dp),
                    )
                    FlowLayout(
                        modifier = Modifier
                            .padding(horizontal = T.ScreenX)
                            .padding(top = 24.dp)
                            .fillMaxWidth()
                            .background(T.Surface, T.ShapeCard)
                            .padding(12.dp),
                        spacing = 4.dp,
                        lineSpacing = 4.dp,
                    ) {
                        instruments.forEachIndexed { i, item ->
                            InstrumentChip(label = item.label, checked = item.on, onToggle = {
                                instruments[i] = item.copy(on = !item.on)
                                saveInstruments()
                            })
                        }
                    }

                    ScreenTitle("Настройки", modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 40.dp))
                    Column(
                        modifier = Modifier
                            .padding(horizontal = T.ScreenX)
                            .padding(top = 24.dp)
                            .fillMaxWidth()
                            .background(T.Surface, T.ShapeCard)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                    ) {
                        if (isAdmin) SettingRow("Администраторы", onAdmins)
                        SettingRow("Push-уведомления", onNotifications)
                        SettingRow("Данные аккаунта", onAccountData)
                        SettingRow("Пользовательское соглашение", onAgreement)
                    }

                    // История: заголовок с кнопкой сортировки; попап поверх списка
                    // (в границах общего Box, чтобы пункты попапа принимали нажатия).
                    Box(
                        modifier = Modifier
                            .padding(horizontal = T.ScreenX)
                            .padding(top = 40.dp)
                            .fillMaxWidth()
                            .heightIn(min = 180.dp),
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                ScreenTitle("История")
                                Spacer(Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .liquidGlass(CircleShape, Fills.Active)
                                        .pressable { sortOpen = !sortOpen },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppIcon(AppIcons.filter, size = 16.dp)
                                }
                            }
                            Column(
                                modifier = Modifier.padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                historyGroups(history, sort).forEach { group ->
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (group.title.isNotEmpty()) {
                                            Text(group.title, style = AppFont.sans(T.FsBase, color = Caption))
                                        }
                                        group.items.forEach { report ->
                                            ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                                        }
                                    }
                                }
                            }
                        }
                        AnimatedSortPopup(
                            visible = sortOpen,
                            sort = sort,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp),
                        ) { sort = it; sortOpen = false }
                    }
                }
            }
        }
    }
}

/** Шапка 309: фото под размытием 50 + чёрный 50 %, аватар 148 на y = 78. */
@Composable
private fun Header(
    avatarUrl: String?,
    initial: String,
    tag: String,
    name: String,
    restricted: Boolean,
    onSettings: () -> Unit,
) {
    val top = designTop(T.TopBarY)
    val height = designTop(309.dp)
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(T.ShapeCard)
                .background(hex("#3b5839")),
        ) {
            RemoteImage(avatarUrl, Modifier.fillMaxSize().blur(50.dp, BlurredEdgeTreatment.Rectangle))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = top),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarCircle(url = avatarUrl, size = 148.dp, initial = initial)
            if (restricted) {
                Text(
                    name, style = AppFont.heading(20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    tag, style = AppFont.sans(T.FsBase), maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    tag, style = AppFont.sans(T.FsBase), maxLines = 1,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        }

        GlassCircleButton(
            AppIcons.settings,
            iconSize = 26.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = T.ScreenX, top = top),
            onClick = onSettings,
        )
    }
}

/** Блок подписки: статус, тариф и «Управление подпиской». */
@Composable
private fun SubscriptionBlock(
    active: Boolean,
    until: String,
    price: String,
    onSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(T.Surface, T.ShapeCard)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Подписка", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (active) "Активна" else "Неактивна",
                        style = AppFont.sans(T.FsMd, FontWeight.SemiBold, color = if (active) T.Green else Caption),
                    )
                    if (active && until.isNotEmpty()) {
                        Text("до $until", style = AppFont.sans(T.FsBase, color = Caption))
                    }
                }
            }
            HairlineDivider()
            Row(modifier = Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Тарифный план", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                Text(price.ifEmpty { "—" }, style = AppFont.sans(T.FsBase, color = Caption))
            }
        }
        AppButton("Управление подпиской", variant = ButtonVariant.Accent) { onSubscription() }
    }
}

/**
 * «Доступный экран профиля» (Figma 474:11853, 484:13055): «Условия» Kreadon 24
 * через 40 от шапки, пояснение 14, блок подписки с «Привязать счет и
 * оплатить»; без тарифа — «Не определен» и подпись 14 белый 65 % под блоком.
 * Если срок подписки уже закончился, показываем дату окончания.
 */
@Composable
private fun ConditionsBlock(price: String, until: String, onSubscription: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 40.dp)) {
        ScreenTitle("Условия")
        Text(
            "Чтобы разблокировать доступ в приложение нужно оплатить ежемесячную подписку",
            style = AppFont.sans(T.FsBase),
            modifier = Modifier.widthIn(max = 325.dp).padding(top = 12.dp),
        )
        Column(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .background(T.Surface, T.ShapeCard)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Подписка", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                    Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                    Text("Неактивна", style = AppFont.sans(T.FsMd, FontWeight.SemiBold, color = T.Error))
                }
                HairlineDivider()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Тарифный план", style = AppFont.sans(T.FsMd, FontWeight.Bold))
                    Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                    Text(price.ifEmpty { "Не определен" }, style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                }
            }
            AppButton("Привязать счет и оплатить", variant = ButtonVariant.Accent) { onSubscription() }
        }
        if (until.isNotEmpty()) {
            Text(
                "Истекла $until. Чтобы продлить доступ, оплатите подписку.",
                style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                modifier = Modifier.widthIn(max = 300.dp).padding(top = 12.dp),
            )
        }
        if (price.isEmpty()) {
            Text(
                "После определение тарифа Вам придет уведомление и стоимость",
                style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                modifier = Modifier.widthIn(max = 300.dp).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(43.dp)
            .pressable(pressAlpha = 1f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = AppFont.sans(T.FsBase), modifier = Modifier.weight(1f))
        AppIcon(AppIcons.back, size = 16.dp, flipX = true)
    }
}

@Composable
private fun AnimatedSortPopup(visible: Boolean, sort: String, modifier: Modifier, onSelect: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(figmaFade()),
        exit = fadeOut(figmaFade()),
        modifier = modifier,
    ) {
        SortPopup(sort, onSelect)
    }
}

/** Попап сортировки (Figma 495:21374): 203, стекло «чёрный 2», радиус 24. */
@Composable
private fun SortPopup(sort: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(203.dp)
            .liquidGlass(T.ShapeCard, Fills.Black2)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SORT_OPTIONS.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(19.dp)
                    .pressable(pressAlpha = 1f) { onSelect(value) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, style = AppFont.sans(T.FsBase), modifier = Modifier.weight(1f))
                if (sort == value) AppIcon(AppIcons.check, size = 20.dp)
            }
        }
    }
}
