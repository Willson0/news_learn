package app.karta.likvidnosti.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ChatAttachmentDto
import app.karta.likvidnosti.data.api.ParticipantDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AvatarCircle
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.CapsuleShape
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.GlassBackButton
import app.karta.likvidnosti.ui.components.GlassCircleButton
import app.karta.likvidnosti.ui.components.RemoteImage
import app.karta.likvidnosti.ui.components.ReportCover
import app.karta.likvidnosti.ui.components.figmaFade
import app.karta.likvidnosti.ui.components.figmaTween
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designTop
import app.karta.likvidnosti.ui.theme.hex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val INFO_TABS = listOf(
    "members" to "Участники",
    "media" to "Медиа",
    "voice" to "Голосовые",
    "files" to "Файлы",
    "links" to "Ссылки",
)

private fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val a = abs(n) % 100
    val b = a % 10
    return when {
        a in 11..19 -> many
        b in 2..4 -> few
        b == 1 -> one
        else -> many
    }
}

private fun mutedPrefs(context: Context) = context.getSharedPreferences("kl_chat", Context.MODE_PRIVATE)

/**
 * «Чат Инфа» (Figma 337:36376 … 337:36561) — порт iOS `ChatInfoView`: шапка
 * #383838 с размытой обложкой чата (blur 50 + чёрный 50 %, высота 376),
 * «назад» и стеклянная кнопка-новость 42 на y=78, аватар 148 по центру с y=78,
 * название Kreadon 20 и «N участников» 14 белый 65 % слева на y=250. С y=321 —
 * лист с радиусом 24 (градиент #1A1B1D → #161719): три стеклянные кнопки 60
 * (звук, поиск, выход), сегменты-чипы 48 (выбранный — пурпур), ниже содержимое
 * вкладки: блоки #25262A со строками 34 (иконка 30, имя SemiBold 12, подпись
 * 12 белый 65 %), сетка медиа 3 × во всю ширину.
 */
@Composable
fun ChatInfoScreen(
    slug: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReport: (Long) -> Unit = {},
    onUser: (Long) -> Unit = {},
    onLeave: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var reportId by remember { mutableStateOf<Long?>(null) }
    var permanent by remember { mutableStateOf(false) }
    var canManage by remember { mutableStateOf<Boolean?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // Пока данные чата не пришли, обложка — мигающий блок, а не заглушка.
    var infoLoaded by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("members") }
    var muted by remember { mutableStateOf(mutedPrefs(context).getBoolean("kl_muted_$slug", false)) }
    // Поиск идёт по сообщениям в самом чате (как в Телеграме): фильтр вкладок здесь не нужен.
    val query = ""

    val participants = remember { mutableStateListOf<ParticipantDto>() }
    val media = remember { mutableStateListOf<ChatAttachmentDto>() }
    val voices = remember { mutableStateListOf<ChatAttachmentDto>() }
    val files = remember { mutableStateListOf<ChatAttachmentDto>() }
    val links = remember { mutableStateListOf<ChatAttachmentDto>() }
    val loaded = remember { mutableStateListOf<String>() }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }

    fun stopPlayer() {
        player?.runCatching { stop() }
        player?.release(); player = null; playingId = null
    }

    fun playVoice(v: ChatAttachmentDto) {
        val url = fileUrl(v.url) ?: return
        if (playingId == v.id) { stopPlayer(); return }
        stopPlayer()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start(); playingId = v.id }
                setOnCompletionListener { stopPlayer() }
                setOnErrorListener { _, _, _ -> stopPlayer(); true }
                prepareAsync()
            }
        }
    }

    fun openUrl(url: String?) {
        val u = fileUrl(url) ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }

    DisposableEffect(Unit) { onDispose { player?.release() } }

    LaunchedEffect(slug) {
        runCatching { Repository.messages(slug) }.getOrNull()?.chat?.let {
            title = it.title
            avatar = fileUrl(it.avatarUrl)
            members = it.subtitle ?: ""
            reportId = it.report
            permanent = it.permanent
            canManage = it.canManage
        }
        infoLoaded = true
    }

    LaunchedEffect(tab) {
        if (tab in loaded) return@LaunchedEffect
        when (tab) {
            "members" -> runCatching { Repository.participants(slug) }.getOrNull()?.let {
                participants.clear(); participants.addAll(it)
                if (members.isEmpty() || !members.contains("участ")) {
                    members = "${it.size} ${pluralRu(it.size, "участник", "участника", "участников")}"
                }
            }
            "media" -> runCatching { Repository.chatAttachments(slug, "image") }.getOrNull()?.let { media.clear(); media.addAll(it) }
            "voice" -> runCatching { Repository.chatAttachments(slug, "voice") }.getOrNull()?.let { voices.clear(); voices.addAll(it) }
            "files" -> runCatching { Repository.chatAttachments(slug, "file") }.getOrNull()?.let { files.clear(); files.addAll(it) }
            "links" -> runCatching { Repository.chatAttachments(slug, "link") }.getOrNull()?.let { links.clear(); links.addAll(it) }
        }
        loaded.add(tab)
    }

    fun matches(vararg parts: String?): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return parts.any { (it ?: "").lowercase().contains(q) }
    }

    fun newsTapped() {
        if (canManage ?: Session.isAdmin) onEdit() else reportId?.let { onReport(it) }
    }

    val headerHeight = designTop(345.dp)

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        contentModifier = Modifier.background(hex("#161719")),
        content = {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    InfoHeader(title, members, avatar, headerHeight, loading = !infoLoaded)

                    // Лист с радиусом 24 поверх нижнего края шапки.
                    Column(
                        modifier = Modifier
                            .padding(top = headerHeight - 24.dp)
                            .fillMaxWidth()
                            .heightIn(min = 535.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to hex("#1a1b1d"),
                                    0.48f to hex("#1e1f21"),
                                    1f to hex("#161719"),
                                ),
                                RoundedCornerShape(topStart = T.RadiusCard, topEnd = T.RadiusCard),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).padding(horizontal = T.ScreenX),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            InfoActionButton(if (muted) AppIcons.bellSlash else AppIcons.bell) {
                                muted = !muted
                                mutedPrefs(context).edit().putBoolean("kl_muted_$slug", muted).apply()
                            }
                            InfoActionButton(AppIcons.search) {
                                // Поиск открывается в чате: возвращаемся к переписке с открытым поиском.
                                ChatSearchBus.request = slug
                                onBack()
                            }
                            if (!permanent) {
                                InfoActionButton(AppIcons.leave) {
                                    if (leaving) return@InfoActionButton
                                    leaving = true
                                    scope.launch {
                                        runCatching { Repository.leaveChat(slug) }.onSuccess { onLeave() }
                                        leaving = false
                                    }
                                }
                            }
                        }
                        // Сегменты-вкладки.
                        Box(
                            modifier = Modifier
                                .padding(top = 40.dp)
                                .padding(horizontal = T.ScreenX)
                                .fillMaxWidth()
                                .liquidGlass(T.ShapeCard)
                                .clip(T.ShapeCard),
                        ) {
                            // Прототип: «Чат Инфа» ↔ «Чат Инфа участники/Голосовые/Файлы/Ссылки» —
                            // плашка вкладки переезжает и меняет ширину: SMART_ANIMATE 800 мс.
                            val tabBounds = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
                            val lensX = remember { Animatable(0f) }
                            val lensW = remember { Animatable(0f) }
                            var lensReady by remember { mutableStateOf(false) }
                            val target = tabBounds[tab]
                            LaunchedEffect(target) {
                                val (x, w) = target ?: return@LaunchedEffect
                                if (!lensReady) {
                                    lensX.snapTo(x.toFloat()); lensW.snapTo(w.toFloat()); lensReady = true
                                } else {
                                    launch { lensX.animateTo(x.toFloat(), figmaTween()) }
                                    lensW.animateTo(w.toFloat(), figmaTween())
                                }
                            }
                            val density = LocalDensity.current
                            Box(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) {
                                if (lensReady) {
                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset(lensX.value.roundToInt(), 0) }
                                            .width(with(density) { lensW.value.coerceAtLeast(0f).toDp() })
                                            .height(48.dp)
                                            .liquidGlass(RoundedCornerShape(20.dp), Fills.Purple),
                                    )
                                }
                                Row {
                                    INFO_TABS.forEach { (key, label) ->
                                        Box(
                                            modifier = Modifier
                                                .height(48.dp)
                                                .onPlaced { tabBounds[key] = it.positionInParent().x.roundToInt() to it.size.width }
                                                .pressable(pressAlpha = 1f) { tab = key }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(label, style = AppFont.sans(T.FsBase)) }
                                    }
                                }
                            }
                        }

                        // Содержимое вкладки растворяется (SMART_ANIMATE 800 мс).
                        Crossfade(
                            targetState = tab,
                            animationSpec = figmaFade(),
                            modifier = Modifier.padding(top = 24.dp, bottom = 40.dp),
                            label = "infoTab",
                        ) { shownTab ->
                            when (shownTab) {
                                "members" -> ListBlock(
                                    participants.filter { matches(it.name, it.username) },
                                    empty = "Пока нет участников",
                                ) { m ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(34.dp).pressable(pressAlpha = 1f) { onUser(m.id) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        AvatarCircle(fileUrl(m.avatarUrl), size = 30.dp, name = m.name.ifEmpty { m.username })
                                        TwoLines(m.name, m.seen ?: "", Modifier.weight(1f))
                                        val role = m.role ?: ""
                                        Text(
                                            role,
                                            style = AppFont.sans(T.FsSm, color = if (role == "Автор") T.Text else T.TextSecondary),
                                            maxLines = 1,
                                        )
                                    }
                                }
                                "media" -> MediaGrid(media) { openUrl(it.url) }
                                "voice" -> ListBlock(voices.filter { matches(it.author, it.meta) }, empty = "Нет голосовых") { a ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(34.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(T.Green, CircleShape)
                                                .pressable(pressAlpha = 1f) { playVoice(a) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (playingId == a.id) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Box(Modifier.size(3.dp, 12.dp).background(Color.White, CapsuleShape))
                                                    Box(Modifier.size(3.dp, 12.dp).background(Color.White, CapsuleShape))
                                                }
                                            } else {
                                                AppIcon(AppIcons.play, size = 16.dp, tint = Color.White, modifier = Modifier.offset(x = 1.dp))
                                            }
                                        }
                                        TwoLines(a.author ?: "Участник", a.meta ?: "", Modifier.weight(1f))
                                    }
                                }
                                "files" -> ListBlock(files.filter { matches(it.name, it.ext) }, empty = "Нет файлов") { f ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(34.dp).pressable(pressAlpha = 1f) { openUrl(f.url) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        FileBadge(f.ext)
                                        TwoLines(f.name ?: "Файл", f.meta ?: "", Modifier.weight(1f))
                                        AppIcon(AppIcons.downloadCloud, size = 32.dp)
                                    }
                                }
                                else -> ListBlock(links.filter { matches(it.title, it.text, it.url) }, empty = "Нет ссылок") { l ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().pressable(pressAlpha = 1f) { openUrl(l.url) },
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(Modifier.size(30.dp).background(hex("#d9d9d9"), RoundedCornerShape(6.dp)))
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Column {
                                                Text(l.title ?: "Ссылка", style = AppFont.sans(T.FsSm, FontWeight.SemiBold))
                                                if (!l.text.isNullOrEmpty()) {
                                                    Text(
                                                        l.text,
                                                        style = AppFont.sans(T.FsSm, color = T.TextSecondary),
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            Text(l.url, style = AppFont.sans(T.FsBase), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        overlay = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = designTop(T.TopBarY))
                    .padding(horizontal = T.ScreenX),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                GlassCircleButton(AppIcons.news, if (canManage ?: Session.isAdmin) "Редактировать чат" else "Открыть отчет", iconSize = 24.dp) { newsTapped() }
            }
        },
    )
}

@Composable
private fun InfoHeader(title: String, members: String, avatar: String?, height: androidx.compose.ui.unit.Dp, loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(hex("#383838")),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(height + 31.dp)) {
            ReportCover(avatar, Modifier.fillMaxSize().blur(50.dp, BlurredEdgeTreatment.Rectangle), loading = loading)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = designTop(78.dp))
                .size(148.dp)
                .clip(CircleShape),
        ) {
            ReportCover(avatar, Modifier.fillMaxSize(), loading = loading)
        }
        Column(
            modifier = Modifier
                .padding(top = designTop(250.dp))
                .padding(horizontal = T.ScreenX),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = AppFont.heading(20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(members, style = AppFont.sans(T.FsBase, color = T.TextSecondary))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.InfoActionButton(
    icon: app.karta.likvidnosti.ui.icons.IconDef,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(60.dp)
            .liquidGlass(T.ShapeCard)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, size = 24.dp) }
}

/** Блок списка: #25262A, радиус 24, строки через линию (12 + 1 + 16). */
@Composable
private fun <Item> ListBlock(items: List<Item>, empty: String, row: @Composable (Item) -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = T.ScreenX)
            .fillMaxWidth()
            .background(T.Surface, T.ShapeCard)
            .padding(vertical = 16.dp),
    ) {
        if (items.isEmpty()) EmptyRow(empty)
        items.forEachIndexed { i, item ->
            if (i > 0) {
                Box(
                    Modifier
                        .padding(top = 15.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f)),
                )
            }
            Box(Modifier.padding(horizontal = 16.dp)) { row(item) }
        }
    }
}

@Composable
private fun TwoLines(top: String, bottom: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(top, style = AppFont.sans(T.FsSm, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(bottom, style = AppFont.sans(T.FsSm, color = T.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MediaGrid(media: List<ChatAttachmentDto>, onOpen: (ChatAttachmentDto) -> Unit) {
    if (media.isEmpty()) {
        EmptyRow("Нет медиа")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        media.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { a ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clipToBounds()
                            .pressable(pressAlpha = 1f) { onOpen(a) },
                    ) {
                        RemoteImage(fileUrl(a.url), Modifier.fillMaxSize(), loading = true)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

/** Значок файла 30×30: цвет по расширению, подпись Bold 12 чёрным. */
@Composable
private fun FileBadge(ext: String?) {
    val e = (ext ?: "").lowercase()
    val color = when (e) {
        "pdf" -> hex("#ff784e")
        "zip", "rar", "7z" -> hex("#d4f04b")
        "exe", "dmg", "doc", "docx" -> hex("#4ea6ff")
        else -> hex("#d9d9d9")
    }
    Box(
        modifier = Modifier.size(30.dp).background(color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (e in listOf("pdf", "zip", "rar", "7z", "exe", "dmg", "doc", "docx")) {
            Text(
                e,
                style = AppFont.sans(if (e.length > 3) 9.sp else T.FsSm, FontWeight.Bold, color = Color.Black),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(max = 28.dp),
            )
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = AppFont.sans(T.FsBase, color = T.TextMuted))
    }
}
