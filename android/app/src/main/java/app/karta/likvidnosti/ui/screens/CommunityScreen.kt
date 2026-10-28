package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
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
import app.karta.likvidnosti.data.api.ChatItemDto
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.ChatRowSkeleton
import app.karta.likvidnosti.ui.components.HairlineDivider
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

private val DEMO_SECTIONS = listOf(
    ChatSectionDto(
        key = "admin", title = "Чат с админом",
        items = listOf(ChatItemDto(id = "admin", title = "Игорь Гломозда", sender = "Игор", preview = "Что интересует?", time = "16:32", unread = 1)),
    ),
    ChatSectionDto(
        key = "main", title = "Чаты",
        items = listOf(ChatItemDto(id = "general", title = "Общий чат", sender = "Артем", preview = "Кто читал новый отчет по золоту?", time = "16:32", unread = 234)),
    ),
    ChatSectionDto(
        key = "reports", title = "Чаты по отчетам",
        items = listOf(
            ChatItemDto(id = "gold", title = "Отчет по золоту 26.02.26", sender = "Вы", preview = "Я вот что не понял это хедж и к...", time = "16:32", unread = 12, pinned = true),
            ChatItemDto(id = "oil", title = "Нефть 12.12.27", sender = "Артем", preview = "Кто читал новый отчет по золоту?", time = "16:32", unread = 234),
            ChatItemDto(id = "plat1", title = "Отчет по платине 26.02.26", sender = "Артем", preview = "Кто читал новый отчет по золоту?", time = "16:32", unread = 234),
        ),
    ),
)

private val Caption = Color.White.copy(alpha = 0.6f)

/**
 * «Сообщество» (iOS CommunityView, Figma 337:27149, админ — 349:12176):
 * поиск-стекло на y = 78, у админа под ним стеклянные сегменты «Все чаты /
 * Личные чаты»; секции — заголовок Kreadon 20, строки Block/Чат 65 с
 * разделителями во всю ширину экрана (белый 10 %). Без подписки — только
 * общий чат под заголовком «Чаты», без поиска.
 */
@Composable
fun CommunityScreen(onOpenChat: (String) -> Unit, navBar: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("all") }
    // Предзагруженные чаты показываем сразу; пока их нет — скелетоны.
    var sections by remember { mutableStateOf(AppData.chats ?: DEMO_SECTIONS) }
    var loaded by remember { mutableStateOf(AppData.chats != null) }
    val isAdmin = Session.isAdmin
    val restricted = Session.restricted

    var refreshing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    suspend fun fetch() {
        val fetched = runCatching { Repository.chats() }.getOrNull()
        if (!fetched.isNullOrEmpty()) {
            AppData.chats = fetched
            sections = fetched
        }
        loaded = true
    }

    LaunchedEffect(Unit) { fetch() }

    val visible = remember(query, tab, sections, isAdmin, restricted) {
        if (restricted) {
            sections.filter { it.key == "main" }.map { ChatSectionDto(it.key, "Чаты", it.items) }
        } else {
            var list = sections
            if (isAdmin) {
                list = list.filter { if (tab == "direct") it.key == "direct" else it.key != "direct" }
            }
            val q = query.trim().lowercase()
            if (q.isEmpty()) list
            else list.map { s ->
                ChatSectionDto(s.key, s.title, s.items.filter { "${it.title} ${it.preview ?: ""}".lowercase().contains(q) })
            }.filter { it.items.isNotEmpty() }
        }
    }

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
                } else {
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
                                        ChatRow(item, Modifier.padding(horizontal = T.ScreenX)) { onOpenChat(item.id) }
                                    }
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        Text(
                            if (isAdmin && tab == "direct") "Личных сообщений пока нет" else "Ничего не найдено",
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

/**
 * Block/Чат: аватар 64, отступ 12; название SemiBold 14, отправитель 14,
 * превью 14 белый 60 %; справа время 14 (и скрепка 12 у закреплённого) и
 * стеклянный счётчик непрочитанных (Regular 10).
 */
@Composable
private fun ChatRow(item: ChatItemDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sender = item.sender
    val preview = item.preview
    val hasDetails = !sender.isNullOrEmpty() || !preview.isNullOrEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 65.dp)
            .pressable(pressAlpha = 1f, onClick = onClick),
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
                Text(item.time ?: "", style = AppFont.sans(T.FsBase, color = Caption))
                if (item.pinned) AppIcon(AppIcons.pinSmall, size = 12.dp)
            }
            if (item.unread > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 19.dp)
                        .widthIn(min = 28.dp)
                        .liquidGlass(CapsuleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.unread.toString(), style = AppFont.sans(10.sp, color = Caption))
                }
            }
        }
    }
}
