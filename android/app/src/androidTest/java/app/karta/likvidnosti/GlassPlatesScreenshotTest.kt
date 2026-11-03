package app.karta.likvidnosti

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.karta.likvidnosti.ui.components.AppToast
import app.karta.likvidnosti.ui.components.AppToastHost
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.ConfirmDialog
import app.karta.likvidnosti.ui.components.FiltersSheet
import app.karta.likvidnosti.ui.components.FiltersResult
import app.karta.likvidnosti.ui.theme.KartaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Снимки плашек поверх контента: размытие должно быть только под плашкой
 * (как backdrop-filter), остальной экран — чёткий. Файлы забирает CI.
 */
@RunWith(AndroidJUnit4::class)
class GlassPlatesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun save(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun calendarBlursOnlyBehindItself() {
        rule.setContent {
            KartaTheme {
                FiltersSheet(initial = FiltersResult(), onApply = {}, onClose = {})
            }
        }
        rule.onNodeWithContentDescription("Выбрать даты").performClick()
        rule.mainClock.advanceTimeBy(1000)
        save("calendar")
    }

    @Test
    fun toastAndDialogBlurOnlyBehindThemselves() {
        rule.setContent {
            KartaTheme {
                BackdropBox(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1E1F21)),
                    content = {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(24) { i ->
                                Box(
                                    Modifier.fillMaxWidth().height(28.dp)
                                        .background(if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF8E24AA)),
                                ) { Text("Строка контента $i — чёткий текст", color = Color.White, fontSize = 14.sp) }
                            }
                        }
                    },
                    overlay = {
                        ConfirmDialog(open = true, title = "Удалить сообщение?", text = "Действие нельзя отменить", onConfirm = {}, onCancel = {})
                        AppToastHost()
                    },
                )
            }
        }
        AppToast.show("Не удалось сохранить, попробуйте ещё раз")
        rule.mainClock.advanceTimeBy(1000)
        save("toast_dialog")
    }
}
