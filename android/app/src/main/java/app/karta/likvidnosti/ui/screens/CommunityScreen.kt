package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ChatItemDto
import app.karta.likvidnosti.data.api.ChatSectionDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.NavBarInset
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.SegmentedControl
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage

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
            ChatItemDto(id = "plat2", title = "Отчет по платине 26.02.26", sender = "Артем", preview = "Кто читал новый отчет по золоту?", time = "16:32", unread = 234),
            ChatItemDto(id = "plat3", title = "Отчет по платине 26.02.26", sender = "Артем", preview = "Кто читал новый отчет по золоту?", time = "16:32", unread = 234),
        ),
    ),
)

@Composable
fun CommunityScreen(onOpenChat: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("all") }
    var sections by remember { mutableStateOf(DEMO_SECTIONS) }
    val isAdmin = Session.isAdmin

    LaunchedEffect(Unit) {
        runCatching { Repository.chats() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { sections = it }
    }

    val visible = remember(query, tab, sections, isAdmin) {
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

    Screen(bottomInset = NavBarInset, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.size(32.dp))
        AppInput(
            query, { query = it },
            placeholder = if (isAdmin && tab == "direct") "Нужный чат" else "Нужный отчет или материал",
            modifier = Modifier.fillMaxWidth(),
        )
        if (isAdmin) {
            SegmentedControl(
                value = tab,
                options = listOf("all" to "Все чаты", "direct" to "Личные чаты"),
                onSelect = { tab = it },
            )
        }

        visible.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenTitle(section.title)
                Column {
                    section.items.forEachIndexed { i, item ->
                        ChatRow(item, last = i == section.items.lastIndex) { onOpenChat(item.id) }
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            Text(
                if (isAdmin && tab == "direct") "Личных сообщений пока нет" else "Ничего не найдено",
                color = T.TextSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
        }
    }
}

@Composable
private fun ChatRow(item: ChatItemDto, last: Boolean, onClick: () -> Unit) {
    val avatar = fileUrl(item.avatarUrl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape),
            )
        } else {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF8A8A8A)))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.title, color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.pinned) AppIcon(AppIcons.Pin, size = 14.dp, tint = T.TextSecondary)
                    Text(item.time ?: "", color = T.TextSecondary, fontSize = T.FsSm)
                }
            }
            if (!item.sender.isNullOrEmpty()) {
                Text(item.sender!!, color = T.Text, fontSize = T.FsBase)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.preview ?: "", color = T.TextSecondary, fontSize = T.FsBase, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (item.unread > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(20.dp)
                            .clip(T.ShapePill)
                            .border(1.dp, Color(0x33FFFFFF), T.ShapePill)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.unread.toString(), color = T.TextSecondary, fontSize = T.FsSm)
                    }
                }
            }
        }
    }
    if (!last) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0FFFFFFF)))
    }
}
