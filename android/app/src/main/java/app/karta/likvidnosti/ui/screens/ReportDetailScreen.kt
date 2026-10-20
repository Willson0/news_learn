package app.karta.likvidnosti.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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
import app.karta.likvidnosti.ui.components.CandlestickChart
import app.karta.likvidnosti.ui.components.GlassCircleButton
import app.karta.likvidnosti.ui.components.GlassLabel
import app.karta.likvidnosti.ui.components.GlassTopBar
import app.karta.likvidnosti.ui.components.ReportActionButton
import app.karta.likvidnosti.ui.components.ReportActionVariant
import app.karta.likvidnosti.ui.components.ReportCover
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import kotlin.math.max

private val DEMO_REPORT = ReportDto(
    title = "Нефть в 2026 году, как она?",
    date = "12.02.26",
    badge = "Актуальный",
    description = "Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее",
    material = "wti",
)

private val HeroSpring = spring<Dp>(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)

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
    var report by remember { mutableStateOf(DEMO_REPORT) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    // HTML-отчёт открывается на весь экран; без html остаётся текстовый блок.
    var htmlOpen by rememberSaveable { mutableStateOf(false) }
    var graphOpen by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val isAdmin = Session.isAdmin

    LaunchedEffect(id) {
        if (id != null) {
            runCatching { Repository.report(id) }.getOrNull()?.let { report = it }
        }
    }

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
                val actionsH = 48.dp * 3 + 12.dp * 2
                val spacer = if (expanded) 24.dp else max(24f, (viewport - heroHeight - actionsH - bottomPad).value).dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomPad),
                ) {
                    Hero(report, heroHeight)
                    Spacer(Modifier.height(spacer))
                    Column(
                        modifier = Modifier.padding(horizontal = T.ScreenX),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ReportActionButton("Открыть отчет", AppIcons.doc, variant = ReportActionVariant.Accent) {
                            if (fileUrl(report.htmlUrl) != null) htmlOpen = true else reportOpen = !reportOpen
                        }
                        ReportActionButton("Обсудить", AppIcons.chat, variant = ReportActionVariant.Pink) {
                            onDiscuss(report.chat)
                        }
                        ReportActionButton(
                            if (graphOpen) "Закрыть график" else "Открыть график",
                            AppIcons.chartBadge,
                            variant = if (graphOpen) ReportActionVariant.ChartOn else ReportActionVariant.ChartOff,
                        ) {
                            val url = report.chartUrl
                            if (!url.isNullOrEmpty()) runCatching { uriHandler.openUri(url) } else graphOpen = !graphOpen
                        }
                    }
                    AnimatedVisibility(visible = reportOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        ReportBody(report, Modifier.padding(horizontal = T.ScreenX).padding(top = 24.dp))
                    }
                    AnimatedVisibility(visible = graphOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        CandlestickChart(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                    }
                }
            }
        },
        overlay = {
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
                    { GlassCircleButton(AppIcons.settings) { onEdit(id) } }
                } else {
                    null
                },
            )
        },
    )
}

@Composable
private fun Hero(report: ReportDto, height: Dp) {
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
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(
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
private fun ReportBody(report: ReportDto, modifier: Modifier = Modifier) {
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
        if (!body.isNullOrEmpty()) {
            Text(body, style = style)
        } else {
            Text(
                "Нефть остаётся одним из ключевых индикаторов мировой экономики. В 2026 году на цену влияют баланс спроса и предложения, политика ОПЕК+ и курс доллара.",
                style = style,
            )
            Text(
                "В этом отчёте мы разбираем основные сценарии, факторы риска и возможные уровни цены на горизонте ближайших месяцев.",
                style = style,
            )
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
