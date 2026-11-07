package app.karta.likvidnosti

import android.graphics.Bitmap
import android.util.Base64
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.karta.likvidnosti.ui.components.BackdropBox
import app.karta.likvidnosti.ui.components.GlassTopBar
import app.karta.likvidnosti.ui.screens.ReportHtmlScreen
import app.karta.likvidnosti.ui.theme.KartaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * HTML-отчёт открывается в Dialog поверх экрана отчёта (BackdropBox со стеклом).
 * Раньше на Android 12+ приложение падало при открытии: стеклянная кнопка «Закрыть»
 * в окне диалога пыталась размыть подложку из окна экрана. Тест открывает тяжёлый
 * HTML (облако из тысяч SVG-точек, как в отчётах DPM) и ждёт отрисовки — без падения.
 */
@RunWith(AndroidJUnit4::class)
class ReportHtmlCrashTest {
    @get:Rule
    val rule = createComposeRule()

    private fun heavyReportHtml(): String = """
        <!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600&display=swap" rel="stylesheet">
        <style>body{font-family:Inter,sans-serif;background:#EEF2F7;color:#0B1C2F;padding:28px 16px}
        .card{background:#fff;border-radius:24px;padding:20px;margin-bottom:18px}
        .mc-canvas{width:100%;height:300px}.mc-svg{width:100%;height:100%;display:block}</style></head>
        <body><div class="card"><h1>DPM · XAU/USD</h1><p>Тестовый отчёт</p></div>
        <div class="card"><div class="mc-canvas" id="mcCanvas"><svg id="mcChart" class="mc-svg"></svg></div></div>
        <script>(function(){const NS='http://www.w3.org/2000/svg';const svg=document.getElementById('mcChart');
        const canvas=document.getElementById('mcCanvas');function draw(){const r=canvas.getBoundingClientRect();
        const W=r.width,H=r.height;if(W<40||H<40){setTimeout(draw,100);return;}
        svg.setAttribute('viewBox','0 0 '+W+' '+H);while(svg.firstChild)svg.removeChild(svg.firstChild);
        const g=document.createElementNS(NS,'g');for(let i=0;i<3400;i++){const c=document.createElementNS(NS,'circle');
        c.setAttribute('cx',(Math.random()*W).toFixed(2));c.setAttribute('cy',(Math.random()*H).toFixed(2));
        c.setAttribute('r',(1.6+Math.random()*1.4).toFixed(2));c.setAttribute('fill','rgb('+(i%255)+',120,200)');
        c.setAttribute('opacity',(0.55+Math.random()*0.45).toFixed(2));g.appendChild(c);}svg.appendChild(g);}
        if(typeof ResizeObserver!=='undefined'){let t;new ResizeObserver(()=>{clearTimeout(t);t=setTimeout(()=>requestAnimationFrame(draw),40)}).observe(canvas);}
        window.addEventListener('load',()=>setTimeout(draw,60));setTimeout(draw,200);setTimeout(draw,600);setTimeout(draw,1200);})();</script>
        </body></html>
    """.trimIndent()

    @Test
    fun openingHtmlReportOverGlassScreenDoesNotCrash() {
        val url = "data:text/html;charset=utf-8;base64," +
            Base64.encodeToString(heavyReportHtml().toByteArray(), Base64.NO_WRAP)
        rule.setContent {
            KartaTheme {
                BackdropBox(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1E1F21)),
                    content = {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(20) { i ->
                                Box(Modifier.fillMaxWidth().height(32.dp).background(if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF8E24AA))) {
                                    Text("Экран отчёта $i", color = Color.White)
                                }
                            }
                        }
                    },
                    overlay = {
                        GlassTopBar(onBack = {})
                        ReportHtmlScreen(url = url, onClose = {})
                    },
                )
            }
        }
        // Даём WebView загрузить страницу и нарисовать облако точек, а стеклу — несколько кадров.
        repeat(8) {
            Thread.sleep(1000)
            rule.waitForIdle()
        }
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots").apply { mkdirs() }
        File(dir, "report_html.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
