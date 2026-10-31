package app.karta.likvidnosti.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.data.api.ReportDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.data.AppData
import app.karta.likvidnosti.ui.components.ChartEmbedView
import app.karta.likvidnosti.ui.components.ChartSource
import app.karta.likvidnosti.ui.components.SkeletonBox
import app.karta.likvidnosti.ui.components.GlassCircleButton
import app.karta.likvidnosti.ui.components.figmaSlow
import app.karta.likvidnosti.ui.components.GlassLabel
import app.karta.likvidnosti.ui.components.LoadErrorState
import app.karta.likvidnosti.ui.components.GlassTopBar
import app.karta.likvidnosti.ui.components.ReportActionButton
import app.karta.likvidnosti.ui.components.ReportActionVariant
import app.karta.likvidnosti.ui.components.ReportCover
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import kotlinx.coroutines.launch
import kotlin.math.max
import app.karta.likvidnosti.data.api.onFailureNotCancelled
import app.karta.likvidnosti.ui.components.SlowNetworkHint

/** Прототип: «Аналитика отчет» 484:13666 ↔ 484:13689 — SMART_ANIMATE, пружина SLOW (≈1.25 с). */
private val HeroSpring = figmaSlow<Dp>()

/**
 * «Аналитика отчет» (iOS ReportDetailView): размытая обложка (blur 25 +
 * чёрный 50 %) высотой 465 со скруглением снизу, сверху — стеклянные «назад»
 * и лейбл статуса (у админа — «настройки»); кнопки прижаты к низу. С открытым
 * отчётом/графиком обложка 302, кнопки сразу под ней, ниже — текст / график.
 */
@Composable
fun ReportDetailScreen(
    id: Long?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDiscuss: (String?) -> Unit,
) {
    // Отчёт из ленты (уже загруженной) показывается сразу; до данных — мигающие блоки,
    // а не демо-заглушка, которая сменялась бы настоящим отчётом.
    val seeded = remember(id) { id?.let { rid -> AppData.reports?.firstOrNull { it.id == rid } } }
    var report by remember(id) { mutableStateOf(seeded ?: ReportDto()) }
    var loaded by remember(id) { mutableStateOf(seeded != null) }
    // Полный отчёт (с текстом) пришёл с сервера; в ленте текста нет.
    var detailLoaded by remember(id) { mutableStateOf(false) }
    var failed by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    // HTML-отчёт открывается на весь экран; без html остаётся текстовый блок.
    var htmlOpen by rememberSaveable { mutableStateOf(false) }
    var graphOpen by rememberSaveable { mutableStateOf(false) }
    val isAdmin = Session.isAdmin

    suspend fun fetch() {
        if (id == null) { failed = true; return }
        runCatching { Repository.report(id) }
            .onSuccess { r ->
                if (r != null) report = r
                detailLoaded = r != null
                failed = r == null
            }
            .onFailureNotCancelled { failed = true }
    }

    fun retry() {
        scope.launch {
            failed = false
            if (seeded == null) loaded = false
            fetch()
            loaded = true
        }
    }

    LaunchedEffect(id) {
        fetch()
        loaded = true
    }
    // Отчёт без текста и без HTML: кнопки «Открыть отчет» нет, вместо неё — честная подпись.
    val hasText = !report.body.isNullOrEmpty() || fileUrl(report.htmlUrl) != null
    val showOpen = !detailLoaded || hasText
    // Не загрузился, и показать нечего — экран ошибки вместо пустой обложки.
    val nothingToShow = failed && seeded == null && !detailLoaded
    val chartSource = remember(report.chartUrl) { ChartSource.parse(report.chartUrl) }

    val expanded = graphOpen || reportOpen
    val heroHeight by animateDpAsState(if (expanded) 302.dp else 465.dp, HeroSpring, label = "hero")
    val bottomPad = designBottom(T.NavBarBottom)

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        contentModifier = Modifier.background(T.Background),
        content = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewport = maxHeight
                // Кнопки (3 × 48 + 2 × 12) прижаты к низу, пока обложка свёрнута не раскрыта.
                // Подпись «Текст ещё не опубликован» занимает место кнопки (48 dp): когда приходит
                // полный отчёт, «Обсудить» и «График» не сдвигаются под пальцем.
                val buttons = if (chartSource != null) 3 else 2
                val actionsH = 48.dp * buttons + 12.dp * (buttons - 1)
                // Отступ над кнопками анимируется той же пружиной, что и обложка: кнопки
                // поднимаются одним блоком вместе с ней, без скачка.
                val spacerTarget = if (expanded) 24.dp else max(24f, (viewport - 465.dp - actionsH - bottomPad).value).dp
                val spacer by animateDpAsState(spacerTarget, HeroSpring, label = "actions")
                if (nothingToShow) {
                    Box(Modifier.fillMaxSize().padding(horizontal = T.ScreenX), contentAlignment = Alignment.Center) {
                        LoadErrorState(onRetry = { retry() }, text = "Не удалось загрузить отчет. Проверьте интернет и попробуйте ещё раз")
                    }
                } else
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomPad),
                ) {
                    Hero(report, heroHeight, loaded)
                    Spacer(Modifier.height(spacer))
                    Column(
                        modifier = Modifier.padding(horizontal = T.ScreenX),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (showOpen) {
                            ReportActionButton("Открыть отчет", AppIcons.doc, variant = ReportActionVariant.Accent) {
                                if (fileUrl(report.htmlUrl) != null) htmlOpen = true else reportOpen = !reportOpen
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
                                Text(
                                    "Текст отчёта ещё не опубликован",
                                    style = AppFont.sans(T.FsBase, color = T.TextSecondary),
                                )
                            }
                        }
                        ReportActionButton("Обсудить", AppIcons.chat, variant = ReportActionVariant.Pink) {
                            onDiscuss(report.chat)
                        }
                        // Без ссылки на график кнопки нет (Figma «Аналитика отчет без графика»).
                        if (chartSource != null) {
                            ReportActionButton(
                                if (graphOpen) "Закрыть график" else "Открыть график",
                                AppIcons.chartBadge,
                                variant = if (graphOpen) ReportActionVariant.ChartOn else ReportActionVariant.ChartOff,
                            ) {
                                graphOpen = !graphOpen
                            }
                        }
                    }
                    AnimatedVisibility(visible = reportOpen, enter = expandVertically(figmaSlow()) + fadeIn(figmaSlow()), exit = shrinkVertically(figmaSlow()) + fadeOut(figmaSlow())) {
                        ReportBody(
                            report,
                            failed = failed,
                            onRetry = { retry() },
                            modifier = Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp),
                        )
                    }
                    // Прототип: «Аналитика отчет график внутри» — блок графика во всю ширину.
                    AnimatedVisibility(visible = graphOpen && chartSource != null, enter = expandVertically(figmaSlow()) + fadeIn(figmaSlow()), exit = shrinkVertically(figmaSlow()) + fadeOut(figmaSlow())) {
                        chartSource?.let { src ->
                            ChartEmbedView(src, Modifier.fillMaxWidth().padding(top = 24.dp).height(600.dp))
                        }
                    }
                }
            }
        },
        overlay = {
            SlowNetworkHint(active = !loaded, bottom = bottomPad)
            if (htmlOpen) {
                fileUrl(report.htmlUrl)?.let { url ->
                    ReportHtmlScreen(url = url, onClose = { htmlOpen = false })
                }
            }
            val badge = report.badge
            GlassTopBar(
                onBack = onBack,
                center = if (!badge.isNullOrEmpty()) {
                    { GlassLabel(badge, height = 42.dp) }
                } else {
                    null
                },
                trailing = if (isAdmin && id != null) {
                    { GlassCircleButton(AppIcons.settings, "Редактировать отчет") { onEdit(id) } }
                } else {
                    null
                },
            )
        },
    )
}

@Composable
private fun Hero(report: ReportDto, height: Dp, loaded: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = T.RadiusCard, bottomEnd = T.RadiusCard)),
        contentAlignment = Alignment.BottomStart,
    ) {
        ReportCover(
            fileUrl(report.coverUrl),
            Modifier.fillMaxSize().blur(25.dp, BlurredEdgeTreatment.Rectangle),
            loading = !loaded,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        if (!loaded) {
            Column(
                modifier = Modifier.padding(horizontal = T.ScreenX).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBox(Modifier.width(220.dp).height(20.dp))
                SkeletonBox(Modifier.width(80.dp).height(14.dp))
                SkeletonBox(Modifier.width(300.dp).height(14.dp))
                SkeletonBox(Modifier.width(240.dp).height(14.dp))
            }
        } else Column(
            modifier = Modifier.padding(horizontal = T.ScreenX).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.title, style = AppFont.heading(20.sp), modifier = Modifier.widthIn(max = 258.dp))
                    val date = report.date
                    if (!date.isNullOrEmpty()) {
                        Text(date, style = AppFont.sans(T.FsBase, color = T.TextSecondary))
                    }
                }
                Spacer(Modifier.width(8.dp))
                AppIcon(AppIcons.material(report.material), size = 20.dp, tint = Color.White)
            }
            Text(
                report.description,
                style = AppFont.sans(T.FsBase, FontWeight.Light),
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
    }
}

@Composable
private fun ReportBody(report: ReportDto, failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val body = report.body
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(T.ShapeCard)
            .background(T.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val style = AppFont.sans(T.FsBase).copy(lineHeight = 21.sp)
        when {
            !body.isNullOrEmpty() -> Text(body, style = style)
            failed -> LoadErrorState(onRetry = onRetry, text = "Не удалось загрузить текст отчета")
            else -> {
                // Текст ещё грузится (из ленты пришёл отчёт без текста).
                SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
                SkeletonBox(Modifier.width(220.dp).height(14.dp))
            }
        }
    }
}

/** HTML-отчёт на весь экран с кнопкой закрытия. Назад тоже закрывает. */
@Composable
private fun ReportHtmlScreen(url: String, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(T.Background)) {
            ReportWebView(url, Modifier.fillMaxSize())
            GlassCircleButton(
                icon = AppIcons.close,
                contentDescription = "Закрыть",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = T.ScreenX, top = 12.dp),
                onClick = onClose,
            )
        }
    }
}

/** HTML-отчёт (WebView, JavaScript включён как в веб-версии). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReportWebView(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadUrl(url)
            }
        },
    )
}
