package app.karta.likvidnosti.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.backdropBlur
import app.karta.likvidnosti.ui.icons.IconDef
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.ChatDates
import app.karta.likvidnosti.data.api.ChatItemDto
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.ChatRowSkeleton
import app.karta.likvidnosti.ui.components.HairlineDivider
import app.karta.likvidnosti.ui.components.LoadErrorState
import app.karta.likvidnosti.ui.components.StaleDataBanner
import app.karta.likvidnosti.ui.components.RemoteImage
import app.karta.likvidnosti.ui.components.SegmentedControl
import app.karta.likvidnosti.ui.components.TabScaffold
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.PullToRefreshBox
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import app.karta.likvidnosti.ui.theme.navBarClearance
import app.karta.likvidnosti.data.api.onFailureNotCancelled
import app.karta.likvidnosti.ui.components.SlowNetworkHint
import app.karta.likvidnosti.ui.components.EmptyState

private val Caption = Color.White.copy(alpha = 0.6f)

/**
 * «Сообщество» (iOS CommunityView, Figma 337:27149, админ — 349:12176):
 * поиск-стекло на y = 78, у админа под ним стеклянные сегменты «Все чаты /
 * Личные чаты»; секции — заголовок Kreadon 20, строки Block/Чат 65 с
 * разделителями во всю ширину экрана (белый 10 %). Без подписки — только
 * общий чат под заголовком «Чаты», без поиска.
 */
@Composable
fun CommunityScreen(
    onOpenChat: (String) -> Unit,
    onOpenInfo: (String) -> Unit,
    navBar: @Composable () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("all") }
    // Предзагруженные чаты показываем сразу; пока их нет — скелетоны.
    var sections by remember { mutableStateOf(AppData.chats ?: emptyList()) }
    var loaded by remember { mutableStateOf(AppData.chats != null) }
    // Список не загрузился: без кэша — экран ошибки, с кэшем — плашка над списком.
    var failed by remember { mutableStateOf(false) }
    val isAdmin = Session.isAdmin
    val restricted = Session.restricted

    var refreshing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    suspend fun fetch() {
        runCatching { Repository.chats() }
            .onSuccess { fetched ->
                AppData.chats = fetched
                AppData.chatsAt = System.currentTimeMillis()
                sections = fetched
                failed = false
            }
            .onFailureNotCancelled { failed = true }
        loaded = true
    }

    fun retry() {
        scope.launch {
            if (sections.isEmpty()) loaded = false
            fetch()
        }
    }

    LaunchedEffect(Unit) { fetch() }

    // Меню по долгому нажатию на чат (как в Телеграме) и подтверждение выхода.
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuItem by remember { mutableStateOf<ChatItemDto?>(null) }
    var leaveTarget by remember { mutableStateOf<ChatItemDto?>(null) }
    // Закреплённые пользователем чаты (на устройстве, у каждого аккаунта свои),
    // последний закреплённый — первым.
    val userId = Session.user?.id ?: 0L
    var pinnedChats by remember(userId) { mutableStateOf(pinnedChats(context, userId)) }

    fun togglePin(item: ChatItemDto) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pinnedChats = if (item.id in pinnedChats) pinnedChats - item.id else listOf(item.id) + pinnedChats
        setPinnedChats(context, userId, pinnedChats)
    }

    fun updateSections(next: List<ChatSectionDto>) {
        sections = next
        AppData.chats = next
    }

    fun markRead(item: ChatItemDto) {
        updateSections(sections.map { s -> ChatSectionDto(s.key, s.title, s.items.map { if (it.id == item.id) it.copy(unread = 0) else it }) })
        scope.launch { runCatching { Repository.markChatRead(item.id) } }
    }

    fun leave(item: ChatItemDto) {
        if (item.id in pinnedChats) togglePin(item)
        val before = sections
        updateSections(
            sections.map { s -> ChatSectionDto(s.key, s.title, s.items.filter { it.id != item.id }) }.filter { it.items.isNotEmpty() },
        )
        scope.launch {
            // Не получилось — возвращаем чат в список.
            runCatching { Repository.leaveChat(item.id) }.onFailure { updateSections(before) }
        }
    }

    val visible = remember(query, tab, sections, isAdmin, restricted, pinnedChats) {
        if (restricted) {
            sections.filter { it.key == "main" }.map { ChatSectionDto(it.key, "Чаты", it.items) }
        } else {
            var list = sections
            if (isAdmin) {
                list = list.filter { if (tab == "direct") it.key == "direct" else it.key != "direct" }
            }
            val q = query.trim().lowercase()
            if (q.isNotEmpty()) {
                list = list.map { s ->
                    ChatSectionDto(s.key, s.title, s.items.filter { "${it.title} ${it.preview ?: ""}".lowercase().contains(q) })
                }.filter { it.items.isNotEmpty() }
            }
            withPinnedOnTop(list, pinnedChats)
        }
    }

    TabScaffold(
        navBar = navBar,
        overlay = {
            SlowNetworkHint(active = !loaded, bottom = navBarClearance())
            ChatListMenu(
                item = menuItem,
                muted = menuItem?.let { chatMuted(context, it.id) } ?: false,
                pinned = menuItem?.let { it.id in pinnedChats } ?: false,
                canPin = !restricted,
                onPin = { item -> menuItem = null; togglePin(item) },
                onClose = { menuItem = null },
                onOpen = { item -> menuItem = null; onOpenChat(item.id) },
                onRead = { item -> menuItem = null; markRead(item) },
                onMute = { item -> menuItem = null; setChatMuted(context, item.id, !chatMuted(context, item.id)) },
                onInfo = { item -> menuItem = null; onOpenInfo(item.id) },
                onLeave = { item -> menuItem = null; leaveTarget = item },
            )
            ConfirmDialog(
                open = leaveTarget != null,
                title = "Выйти из чата?",
                text = leaveTarget?.let { "Вы больше не будете видеть «${it.title}» в списке чатов." } ?: "",
                confirmLabel = "Выйти",
                onConfirm = { leaveTarget?.let { leave(it) }; leaveTarget = null },
                onCancel = { leaveTarget = null },
            )
        },
    ) {
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
                    .padding(top = designTop(T.TopBarY), bottom = navBarClearance()),
            ) {
                if (!restricted) {
                    AppInput(
                        query, { query = it },
                        placeholder = if (isAdmin && tab == "direct") "Нужный чат" else "Нужный отчет или материал",
                        modifier = Modifier.padding(horizontal = T.ScreenX),
                    )
                }
                if (isAdmin) {
                    SegmentedControl(
                        value = tab,
                        options = listOf("all" to "Все чаты", "direct" to "Личные чаты"),
                        onSelect = { tab = it },
                        glass = true,
                        modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 12.dp),
                    )
                }

                if (!loaded) {
                    Column(modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp)) {
                        repeat(6) { ChatRowSkeleton() }
                    }
                } else if (failed && sections.isEmpty()) {
                    LoadErrorState(onRetry = { retry() }, modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp))
                } else {
                    if (failed) {
                        StaleDataBanner(AppData.chatsAt, onRetry = { retry() }, modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp))
                    }
                    Column(
                        modifier = Modifier.padding(top = if (restricted) 0.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(40.dp),
                    ) {
                        visible.forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text(
                                    section.title,
                                    style = AppFont.heading(20.sp),
                                    modifier = Modifier.padding(horizontal = T.ScreenX),
                                )
                                Column {
                                    section.items.forEachIndexed { idx, item ->
                                        if (idx > 0) HairlineDivider(Modifier.padding(vertical = 12.dp))
                                        ChatRow(
                                            item,
                                            Modifier.padding(horizontal = T.ScreenX),
                                            muted = chatMuted(context, item.id),
                                            userPinned = item.id in pinnedChats,
                                            onLongPress = { menuItem = item },
                                        ) { onOpenChat(item.id) }
                                    }
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        // Пустой поиск и пустой список — разные состояния: у поиска есть «Сбросить».
                        if (query.isNotBlank()) {
                            EmptyState(
                                "По запросу «${query.trim()}» ничего не найдено",
                                modifier = Modifier.padding(horizontal = T.ScreenX),
                                actionLabel = "Сбросить поиск",
                                onAction = { query = "" },
                            )
                        } else {
                            Text(
                                if (isAdmin && tab == "direct") "Личных сообщений пока нет" else "Чатов пока нет",
                                style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Block/Чат: аватар 64, отступ 12; название SemiBold 14, отправитель 14,
 * превью 14 белый 60 %; справа время 14 (и скрепка 12 у закреплённого) и
 * стеклянный счётчик непрочитанных (Regular 10).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    item: ChatItemDto,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    userPinned: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val sender = item.sender
    val preview = item.preview
    val hasDetails = !sender.isNullOrEmpty() || !preview.isNullOrEmpty()
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 65.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress?.let { open ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        open()
                    }
                },
            ),
        verticalAlignment = if (hasDetails) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (hasDetails) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(hex("#797979")),
                contentAlignment = Alignment.Center,
            ) {
                val url = fileUrl(item.avatarUrl)
                if (url != null) {
                    RemoteImage(url, Modifier.size(64.dp).clip(CircleShape), loading = true) {
                        Text(item.title.take(1).uppercase(), style = AppFont.heading(24.sp), modifier = Modifier.align(Alignment.Center))
                    }
                } else {
                    Text(item.title.take(1).uppercase(), style = AppFont.heading(24.sp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    style = AppFont.sans(T.FsBase, FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!sender.isNullOrEmpty()) {
                    Text(sender, style = AppFont.sans(T.FsBase), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!preview.isNullOrEmpty()) {
                    Text(preview, style = AppFont.sans(T.FsBase, color = Caption), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(
            modifier = Modifier.padding(top = if (hasDetails) 3.dp else 0.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier.height(19.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (muted) AppIcon(AppIcons.bellSlash, size = 14.dp, tint = Caption)
                Text(ChatDates.listStamp(item.lastAt, item.time), style = AppFont.sans(T.FsBase, color = Caption))
                if (item.pinned || userPinned) AppIcon(AppIcons.pinSmall, size = 12.dp)
            }
            if (item.unread > 0) {
                // Непрочитанные: зелёный бейдж 12; у чата без звука — серый.
                Box(
                    modifier = Modifier
                        .padding(top = 17.dp)
                        .widthIn(min = 24.dp)
                        .background(if (muted) hex("#5a5b5f") else T.Green, CapsuleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (item.unread > 999) "999+" else item.unread.toString(),
                        style = AppFont.sans(12.sp, FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

private fun chatPrefs(context: Context) = context.getSharedPreferences("kl_chat", Context.MODE_PRIVATE)

/** «Без звука» — тот же флаг, что в инфо чата. */
private fun chatMuted(context: Context, slug: String) = chatPrefs(context).getBoolean("kl_muted_$slug", false)

private fun setChatMuted(context: Context, slug: String, muted: Boolean) =
    chatPrefs(context).edit().putBoolean("kl_muted_$slug", muted).apply()

/** Закреплённые чаты аккаунта: id через перевод строки, последний закреплённый — первым. */
private fun pinnedChats(context: Context, userId: Long): List<String> =
    chatPrefs(context).getString("kl_pinned_chats_$userId", null)
        ?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

private fun setPinnedChats(context: Context, userId: Long, ids: List<String>) =
    chatPrefs(context).edit().putString("kl_pinned_chats_$userId", ids.joinToString("\n")).apply()

/** Закреплённые чаты — отдельной секцией над остальными (из своих секций убираются). */
private fun withPinnedOnTop(sections: List<ChatSectionDto>, pinned: List<String>): List<ChatSectionDto> {
    val all = sections.flatMap { it.items }
    val pinnedItems = pinned.mapNotNull { id -> all.firstOrNull { it.id == id } }
    if (pinnedItems.isEmpty()) return sections
    val rest = sections.map { s -> ChatSectionDto(s.key, s.title, s.items.filter { it.id !in pinned }) }
        .filter { it.items.isNotEmpty() }
    return listOf(ChatSectionDto("pinned", "Закрепленные", pinnedItems)) + rest
}

/**
 * Меню чата по долгому нажатию (как в Телеграме): фон размывается и
 * затемняется, чат поднимается карточкой, под ним — стеклянное меню.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ChatListMenu(
    item: ChatItemDto?,
    muted: Boolean,
    pinned: Boolean,
    canPin: Boolean,
    onClose: () -> Unit,
    onOpen: (ChatItemDto) -> Unit,
    onRead: (ChatItemDto) -> Unit,
    onPin: (ChatItemDto) -> Unit,
    onMute: (ChatItemDto) -> Unit,
    onInfo: (ChatItemDto) -> Unit,
    onLeave: (ChatItemDto) -> Unit,
) {
    // Последний показанный чат держим, пока меню плавно закрывается.
    var shown by remember { mutableStateOf<ChatItemDto?>(null) }
    if (item != null) shown = item
    BackHandler(enabled = item != null) { onClose() }
    AnimatedVisibility(
        visible = item != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
    ) {
        shown?.let { chat -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropBlur(13.dp)
                    .background(hex("#070707").copy(alpha = 0.7f))
                    .pressable(pressAlpha = 1f) { onClose() },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.ScreenX)
                    .animateEnterExit(
                        enter = scaleIn(tween(240, easing = FastOutSlowInEasing), initialScale = 0.94f),
                        exit = scaleOut(tween(160), targetScale = 0.96f),
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(T.ShapeCard)
                        .background(T.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    ChatRow(chat) { onOpen(chat) }
                }
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .liquidGlass(T.ShapeCard)
                        .pressable(pressAlpha = 1f) {}
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChatMenuRow("Открыть", AppIcons.chat) { onOpen(chat) }
                    if (chat.unread > 0) ChatMenuRow("Отметить прочитанным", AppIcons.check) { onRead(chat) }
                    if (canPin) ChatMenuRow(if (pinned) "Открепить" else "Закрепить", AppIcons.pin) { onPin(chat) }
                    ChatMenuRow(if (muted) "Включить звук" else "Без звука", if (muted) AppIcons.bell else AppIcons.bellSlash) { onMute(chat) }
                    ChatMenuRow("Информация о чате", AppIcons.dots) { onInfo(chat) }
                    if (!chat.permanent) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        ChatMenuRow("Выйти из чата", AppIcons.leave, color = T.Error) { onLeave(chat) }
                    }
                }
            }
        } }
    }
}

@Composable
private fun ChatMenuRow(title: String, icon: IconDef, color: Color = T.Text, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { AppIcon(icon, size = 20.dp, tint = color) }
        Text(title, style = AppFont.sans(T.FsBase, color = color), maxLines = 1)
    }
}
