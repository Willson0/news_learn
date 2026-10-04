package app.karta.likvidnosti.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Repository
import app.karta.likvidnosti.data.Uploads
import app.karta.likvidnosti.data.api.ApiException
import app.karta.likvidnosti.data.api.InstrumentDto
import app.karta.likvidnosti.data.fileUrl
import app.karta.likvidnosti.ui.components.AppButton
import app.karta.likvidnosti.ui.components.ButtonVariant
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.components.ScreenTitle
import app.karta.likvidnosti.ui.icons.AppIcon
import app.karta.likvidnosti.ui.icons.AppIcons
import app.karta.likvidnosti.ui.theme.T
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private const val DESCRIPTION_MAX = 250
private val SHORT_LABELS = mapOf("gas" to "Нат. газ")

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
        if (!validate()) return
        saving = true
        errors = errors - "form"
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
                errors = if (fe.isNotEmpty()) fe else mapOf("form" to (ex.message ?: "Не удалось сохранить"))
            } catch (ex: Exception) {
                errors = mapOf("form" to "Не удалось сохранить")
            } finally {
                saving = false
            }
        }
    }

    fun remove() {
        deleting = true
        scope.launch {
            try {
                Repository.deleteReport(reportId!!)
                onDeleted()
            } catch (ex: Exception) {
                errors = mapOf("form" to ((ex as? ApiException)?.message ?: "Не удалось удалить"))
                confirmOpen = false
            } finally {
                deleting = false
            }
        }
    }

    Screen(verticalArrangement = Arrangement.spacedBy(12.dp), bottomInset = 32.dp) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp), horizontalArrangement = Arrangement.End) {
            if (isEdit) {
                Box(
                    modifier = Modifier
                        .size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(T.SurfaceMuted).border(1.dp, Color(0x1FFFFFFF), androidx.compose.foundation.shape.CircleShape)
                        .clickable { confirmOpen = true },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.TrashX, size = 22.dp, tint = Color(0xFFE0262B)) }
            }
        }

        ScreenTitle(if (isEdit) "Редактирование отчета" else "Создание отчета")

        RField("Заголовок", title, error = errors["title"]) { title = it; clearError("title") }
        FormError(errors["title"])

        RField(
            label = "Описание",
            value = description,
            error = errors["description"],
            counter = "${description.length}/$DESCRIPTION_MAX символов",
            singleLine = false,
        ) { if (it.length <= DESCRIPTION_MAX) description = it; clearError("description") }
        FormError(errors["description"])

        RField("График", chartUrl, placeholder = "Ссылка на график", error = errors["chart_url"]) {
            chartUrl = it; clearError("chart_url")
        }
        FormError(errors["chart_url"])

        // Инструменты
        FlowRow(
            modifier = Modifier.fillMaxWidth().clip(T.ShapeCard).background(T.Surface).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            instruments.forEach { item ->
                val on = instrument == item.key
                val label = SHORT_LABELS[item.key] ?: item.label
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(T.ShapeChip)
                        .then(if (on) Modifier.background(T.GradientAccent) else Modifier.border(1.dp, Color(0x2EFFFFFF), T.ShapeChip))
                        .clickable {
                            instrument = if (instrument == item.key) null else item.key
                            clearError("instrument")
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = T.Text, fontSize = T.FsBase, maxLines = 1) }
            }
        }
        FormError(errors["instrument"])

        // HTML
        FileRow(
            name = htmlName.ifEmpty { "Отчет html" },
            hasFile = htmlName.isNotEmpty(),
            preview = null,
            iconHtml = true,
            onAdd = { htmlPicker.launch("text/html") },
            onRemove = { htmlUri = null; htmlName = ""; removeHtml = isEdit },
        )
        FormError(errors["html"])

        // Обложка
        FileRow(
            name = coverName.ifEmpty { "Обложка" },
            hasFile = coverName.isNotEmpty(),
            preview = coverUri ?: coverPreview,
            iconHtml = false,
            onAdd = { coverPicker.launch("image/*") },
            onRemove = { coverUri = null; coverName = ""; coverPreview = null; removeCover = isEdit },
        )
        FormError(errors["cover"])

        errors["form"]?.let {
            Text(it, color = T.Error, fontSize = T.FsSm, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        AppButton(
            text = if (isEdit) "Сохранить" else "Создать отчет",
            variant = ButtonVariant.Accent,
            enabled = !saving,
            modifier = Modifier.padding(top = 16.dp),
        ) { submit() }
    }

    ConfirmDialog(
        open = confirmOpen,
        title = "Удалить отчет?",
        text = "Удалив отчет он пропадет у всех пользователей, включая чат",
        busy = deleting,
        onConfirm = { remove() },
        onCancel = { confirmOpen = false },
    )
}

@Composable
private fun RField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String = "Введите текст",
    error: String? = null,
    counter: String? = null,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(T.SurfaceMuted)
            .border(1.dp, if (error != null) T.Error else Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = T.TextSecondary, fontSize = T.FsSm)
            if (counter != null) Text(counter, color = T.TextSecondary, fontSize = T.FsSm)
        }
        Box {
            if (value.isEmpty()) Text(placeholder, color = T.TextMuted, fontSize = T.FsBase)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(color = T.Text, fontSize = T.FsBase),
                cursorBrush = SolidColor(T.Text),
                modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 60.dp)),
            )
        }
    }
}

@Composable
private fun FileRow(
    name: String,
    hasFile: Boolean,
    preview: Any?,
    iconHtml: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).clip(T.ShapeCard).background(T.Surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                .then(if (hasFile && !iconHtml) Modifier.background(Color.White) else if (!hasFile) Modifier.background(Color.White) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hasFile && !iconHtml && preview != null ->
                    AsyncImage(model = preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(32.dp))
                hasFile && iconHtml -> AppIcon(AppIcons.Doc, size = 32.dp)
                else -> AppIcon(AppIcons.Plus, size = 22.dp, tint = Color(0xFF6FBF1F))
            }
        }
        Text(name, color = T.Text, fontSize = T.FsMd, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.height(32.dp).clip(T.ShapePill).background(T.GradientPurpleSolid)
                .clickable { if (hasFile) onRemove() else onAdd() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (hasFile) "Удалить" else "Добавить", color = T.Text, fontSize = T.FsBase, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun FormError(text: String?) {
    if (text != null) Text(text, color = T.Error, fontSize = T.FsSm)
}
