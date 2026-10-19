package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.api.InstrumentDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.AppInput
import app.karta.likvidnosti.ui.components.AuthField
import app.karta.likvidnosti.ui.components.AuthFieldError
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.FileHtmlIcon
import app.karta.likvidnosti.ui.components.Fills
import app.karta.likvidnosti.ui.components.FlowLayout
import app.karta.likvidnosti.ui.components.GlassCircleButton
import app.karta.likvidnosti.ui.components.GlassTopBar
import app.karta.likvidnosti.ui.components.PurplePillButton
import app.karta.likvidnosti.ui.components.liquidGlass
import app.karta.likvidnosti.ui.components.pressable
import app.karta.likvidnosti.ui.components.screenBackground
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.AppFont
import app.karta.likvidnosti.ui.theme.T
import app.karta.likvidnosti.ui.theme.designBottom
import app.karta.likvidnosti.ui.theme.designTop
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private const val DESCRIPTION_MAX = 250
private val SHORT_LABELS = mapOf("gas" to "Нат. газ")

/**
 * «Создание и загрузка отчета» (iOS ReportFormView, Figma 349:11597, 349:11743,
 * ошибки 349:12486, редактирование 349:12705): «назад» (и красная корзина при
 * редактировании) на y=78, заголовок Kreadon 24 на y=144; поля со стеклом и
 * подписью: «Заголовок» 63, «Описание» ≥101 со счётчиком «N/250 символов»
 * справа, «График» 63; блок чипов-инструментов (#25262A, радиус 24, отступ 12,
 * шаг 4); строки файлов 64 («Отчет html», «Обложка»: белая иконка 32 с
 * зелёным «+», пурпурная пилюля «Добавить/Удалить»); ошибки 12 красным 70 %
 * под полем; внизу кнопка (низ — 45).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportFormScreen(
    reportId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val isEdit = reportId != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var chartUrl by remember { mutableStateOf("") }
    var instrument by remember { mutableStateOf<String?>(null) }
    var instruments by remember { mutableStateOf<List<InstrumentDto>>(emptyList()) }

    var htmlUri by remember { mutableStateOf<Uri?>(null) }
    var htmlName by remember { mutableStateOf("") }
    var removeHtml by remember { mutableStateOf(false) }

    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var coverName by remember { mutableStateOf("") }
    var coverPreview by remember { mutableStateOf<String?>(null) }
    var removeCover by remember { mutableStateOf(false) }

    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var saving by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    fun clearError(key: String) {
        if (errors[key] != null) errors = errors - key
    }

    val htmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            htmlUri = uri
            htmlName = Uploads.displayName(context, uri)
            removeHtml = false
            clearError("html")
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coverUri = uri
            coverName = Uploads.displayName(context, uri)
            coverPreview = null
            removeCover = false
            clearError("cover")
        }
    }

    LaunchedEffect(reportId) {
        runCatching { Repository.instruments() }.getOrNull()?.let { instruments = it }
        if (reportId != null) {
            val r = runCatching { Repository.report(reportId) }.getOrNull()
            if (r == null) {
                onBack()
                return@LaunchedEffect
            }
            title = r.title ?: ""
            description = r.description ?: ""
            chartUrl = r.chartUrl ?: ""
            instrument = r.material
            htmlName = r.htmlName ?: ""
            coverName = r.coverName ?: ""
            coverPreview = fileUrl(r.coverUrl)
        }
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (title.trim().isEmpty()) e["title"] = "Напишите название"
        if (description.trim().isEmpty()) e["description"] = "Напишите описание"
        if (instrument == null) e["instrument"] = "Выберите инструмент"
        if (coverName.isEmpty()) e["cover"] = "Загрузите обложку"
        errors = e
        return e.isEmpty()
    }

    fun submit() {
        if (!validate() || saving) return
        saving = true
        scope.launch {
            try {
                val parts = buildList {
                    add(Uploads.textPart("title", title.trim()))
                    add(Uploads.textPart("description", description.trim()))
                    add(Uploads.textPart("chart_url", chartUrl.trim()))
                    add(Uploads.textPart("instrument", instrument ?: ""))
                    coverUri?.let { Uploads.filePart(context, "cover", it)?.let { p -> add(p) } }
                    htmlUri?.let { Uploads.filePart(context, "html", it)?.let { p -> add(p) } }
                    if (removeCover) add(Uploads.textPart("remove_cover", "1"))
                    if (removeHtml) add(Uploads.textPart("remove_html", "1"))
                }
                val report = if (reportId != null) {
                    Repository.updateReport(reportId, parts)
                } else {
                    Repository.createReport(parts)
                }
                if (report != null) onSaved(report.id) else errors = mapOf("form" to "Не удалось сохранить")
            } catch (ex: ApiException) {
                val fe = ex.errors.mapValues { (_, v) -> v.firstOrNull() ?: "" }.filterValues { it.isNotEmpty() }
                errors = fe.ifEmpty { mapOf("form" to ex.message) }
            } catch (ex: Exception) {
                errors = mapOf("form" to "Не удалось сохранить")
            } finally {
                saving = false
            }
        }
    }

    fun remove() {
        val id = reportId ?: return
        deleting = true
        scope.launch {
            try {
                Repository.deleteReport(id)
                onDeleted()
            } catch (ex: ApiException) {
                errors = mapOf("form" to ex.message)
                confirmOpen = false
            } catch (ex: Exception) {
                errors = mapOf("form" to "Не удалось удалить")
                confirmOpen = false
            } finally {
                deleting = false
            }
        }
    }

    BackdropBox(
        modifier = Modifier.fillMaxSize(),
        contentModifier = Modifier.screenBackground(),
        content = {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = T.ScreenX)
                        .padding(top = designTop(144.dp), bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (isEdit) "Редактирование отчета" else "Создание отчета",
                        style = AppFont.heading(T.FsTitle),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    AuthField(errors["title"]) {
                        AppInput(
                            title,
                            { title = it; clearError("title") },
                            placeholder = "Введите текст",
                            label = "Заголовок",
                            error = errors.containsKey("title"),
                            height = 63.dp,
                        )
                    }

                    AuthField(errors["description"]) {
                        DescriptionInput(description, error = errors.containsKey("description")) {
                            description = it.take(DESCRIPTION_MAX)
                            clearError("description")
                        }
                    }

                    AuthField(errors["chart_url"]) {
                        AppInput(
                            chartUrl,
                            { chartUrl = it; clearError("chart_url") },
                            placeholder = "Ссылка на график",
                            label = "График",
                            error = errors.containsKey("chart_url"),
                            keyboardType = KeyboardType.Uri,
                            height = 63.dp,
                        )
                    }

                    AuthField(errors["instrument"]) {
                        FlowLayout(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(T.ShapeCard)
                                .background(T.Surface)
                                .padding(12.dp),
                            spacing = 4.dp,
                        ) {
                            instruments.forEach { item ->
                                val on = instrument == item.key
                                InstrumentOption(SHORT_LABELS[item.key] ?: item.label, on) {
                                    instrument = if (on) null else item.key
                                    clearError("instrument")
                                }
                            }
                        }
                    }

                    AuthField(errors["html"]) {
                        FileRow(
                            name = htmlName.ifEmpty { "Отчет html" },
                            has = htmlName.isNotEmpty(),
                            icon = {
                                if (htmlName.isEmpty()) AddIcon() else FileHtmlIcon(Modifier.size(30.dp))
                            },
                            onAdd = { htmlPicker.launch("text/html") },
                            onRemove = {
                                htmlUri = null
                                htmlName = ""
                                removeHtml = isEdit
                            },
                        )
                    }

                    AuthField(errors["cover"]) {
                        FileRow(
                            name = coverName.ifEmpty { "Обложка" },
                            has = coverName.isNotEmpty(),
                            icon = {
                                if (coverName.isEmpty()) {
                                    AddIcon()
                                } else {
                                    AsyncImage(
                                        model = coverUri ?: coverPreview,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White),
                                    )
                                }
                            },
                            onAdd = { coverPicker.launch("image/*") },
                            onRemove = {
                                coverUri = null
                                coverName = ""
                                coverPreview = null
                                removeCover = isEdit
                            },
                        )
                    }

                    errors["form"]?.let { AuthFieldError(it) }
                }

                AppButton(
                    if (isEdit) "Сохранить" else "Создать отчет",
                    variant = ButtonVariant.Accent,
                    enabled = !saving,
                    modifier = Modifier
                        .padding(horizontal = T.ScreenX)
                        .padding(top = 12.dp, bottom = designBottom(T.NavBarBottom)),
                ) { submit() }
            }
        },
        overlay = {
            GlassTopBar(
                onBack = onBack,
                trailing = if (isEdit) {
                    { GlassCircleButton(AppIcons.trashX, iconSize = 24.dp, iconColor = T.Error) { confirmOpen = true } }
                } else {
                    null
                },
            )
            ConfirmDialog(
                open = confirmOpen,
                title = "Удалить отчет?",
                text = "Удалив отчет он пропадет у всех пользователей, включая чат",
                busy = deleting,
                onConfirm = { remove() },
                onCancel = { confirmOpen = false },
            )
        },
    )
}

/** «Описание»: стекло, подпись и счётчик 12 сверху, многострочный ввод 14, высота от 101. */
@Composable
private fun DescriptionInput(value: String, error: Boolean, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 101.dp)
            .liquidGlass(T.ShapeInput)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Описание", style = AppFont.sans(T.FsSm, color = T.TextSecondary), modifier = Modifier.weight(1f))
            Text("${value.length}/$DESCRIPTION_MAX символов", style = AppFont.sans(T.FsSm, color = T.TextSecondary))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = AppFont.sans(T.FsBase, color = if (error) T.Error else T.Text),
            cursorBrush = SolidColor(T.Green),
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Введите текст", style = AppFont.sans(T.FsBase, color = T.TextMuted))
                    }
                    inner()
                }
            },
        )
    }
}

/** Чип инструмента (14, отступ 24, высота 40, радиус 12): выбранный — «active». */
@Composable
private fun InstrumentOption(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .liquidGlass(RoundedCornerShape(12.dp), if (on) Fills.Active else Fills.Glass)
            .pressable(pressAlpha = 1f, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppFont.sans(T.FsBase), maxLines = 1)
    }
}

/** Белая плашка 32 (радиус 8) с зелёным «+» 24. */
@Composable
private fun AddIcon() {
    Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.plus, size = 24.dp, tint = T.Green)
    }
}

/** Строка файла 64 (#25262A, радиус 24): иконка 32, имя 16, пурпурная «Добавить/Удалить». */
@Composable
private fun FileRow(
    name: String,
    has: Boolean,
    icon: @Composable () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(T.ShapeCard)
            .background(T.Surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            name,
            style = AppFont.sans(T.FsMd),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PurplePillButton(if (has) "Удалить" else "Добавить") { if (has) onRemove() else onAdd() }
    }
}
