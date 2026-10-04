package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.InstrumentChip
import app.karta.likvidnosti.ui.components.NavBarInset
import app.karta.likvidnosti.ui.components.ReportCard
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.components.insetBottom
import app.karta.likvidnosti.ui.components.insetTop
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private data class ProfInstrument(val key: String, val label: String, val on: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onSettings: () -> Unit,
    onSubscription: () -> Unit,
    onNotifications: () -> Unit,
    onAccountData: () -> Unit,
    onAdmins: () -> Unit,
    onOpenReport: (Long) -> Unit,
) {
    var tag by remember { mutableStateOf(Session.user?.username ?: "") }
    var avatar by remember { mutableStateOf(Session.user?.avatarUrl) }
    var subActive by remember { mutableStateOf(false) }
    var subUntil by remember { mutableStateOf("") }
    var subPlan by remember { mutableStateOf("") }
    val instruments = remember { mutableStateListOf<ProfInstrument>() }
    var history by remember { mutableStateOf<List<ReportDto>>(emptyList()) }
    var sortOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("new") }
    val scope = rememberCoroutineScope()
    val isAdmin = Session.isAdmin

    LaunchedEffect(Unit) {
        runCatching { Repository.profile() }.getOrNull()?.let { p ->
            p.user?.let { tag = it.username ?: tag; avatar = it.avatarUrl ?: avatar }
            p.subscription?.let { subActive = it.active; subUntil = it.until ?: ""; subPlan = it.plan ?: "" }
            instruments.clear()
            instruments.addAll(p.instruments.map { ProfInstrument(it.key, it.label, it.on) })
        }
        history = runCatching { Repository.reports() }.getOrDefault(emptyList())
    }

    fun saveInstruments() {
        scope.launch { runCatching { Repository.syncInstruments(instruments.filter { it.on }.map { it.key }) } }
    }

    val avatarInitial = tag.removePrefix("@").take(1).uppercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(T.Bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = insetBottom() + NavBarInset),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF3A3A32), Color(0xFF232420))),
                )
                .padding(top = insetTop() + 56.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = T.ScreenX, top = 0.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(T.SurfaceMuted)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Settings, size = 22.dp) }

            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val url = fileUrl(avatar)
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF6B7350), Color(0xFF3F4A2C))))
                        .border(3.dp, Color(0x1FFFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (url != null) {
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else if (avatarInitial.isNotEmpty()) {
                        Text(avatarInitial, color = T.Text, fontSize = T.FsTitle, fontWeight = FontWeight.Bold)
                    }
                }
                if (tag.isNotEmpty()) Text(tag, color = T.Text, fontSize = T.FsLg, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(horizontal = T.ScreenX), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Subscription
            Column(
                modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Подписка", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (subActive) "Активна" else "Неактивна", color = T.Green, fontSize = T.FsBase, fontWeight = FontWeight.SemiBold)
                        if (subUntil.isNotEmpty()) Text("до $subUntil", color = T.TextSecondary, fontSize = T.FsBase)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Тарифный план", color = T.Text, fontSize = T.FsMd, fontWeight = FontWeight.SemiBold)
                    Text(subPlan, color = T.TextSecondary, fontSize = T.FsBase)
                }
                AppButton("Управление подпиской", variant = ButtonVariant.Accent) { onSubscription() }
            }

            // Instruments
            ScreenTitle("Отслеживаемые инструменты")
            Box(modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    instruments.forEachIndexed { i, item ->
                        InstrumentChip(label = item.label, checked = item.on, onToggle = {
                            instruments[i] = item.copy(on = !item.on)
                            saveInstruments()
                        })
                    }
                }
            }

            // Settings
            ScreenTitle("Настройки")
            Column {
                val items = buildList<Pair<String, () -> Unit>> {
                    if (isAdmin) add("Администраторы" to onAdmins)
                    add("Push-уведомления" to onNotifications)
                    add("Данные аккаунта" to onAccountData)
                    add("Пользовательское соглашение" to {})
                }
                items.forEachIndexed { i, (label, action) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { action() }.padding(horizontal = 4.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = T.Text, fontSize = T.FsMd)
                        AppIcon(AppIcons.ChevronRight, size = 18.dp, tint = T.TextSecondary)
                    }
                    if (i != items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                }
            }

            // History
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle("История")
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(T.GradientAccent).clickable { sortOpen = !sortOpen },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(AppIcons.Sort, size = 22.dp) }
                }
                if (sortOpen) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 52.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(Color(0xFF2C2D30))
                            .padding(6.dp),
                    ) {
                        listOf("new" to "Сначала новые", "old" to "Сначала Старые", "name" to "По названию").forEach { (v, label) ->
                            Row(
                                modifier = Modifier.width(190.dp).clickable { sort = v; sortOpen = false }.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, color = if (sort == v) T.Text else T.TextSecondary, fontSize = T.FsBase)
                                if (sort == v) AppIcon(AppIcons.CheckSquare, size = 16.dp, tint = T.Text)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (history.isNotEmpty()) {
                    Text("Недавние", color = T.TextSecondary, fontSize = T.FsBase)
                    history.forEach { report ->
                        ReportCard(report = report, onOpen = { onOpenReport(report.id) })
                    }
                }
            }
        }
    }
}
