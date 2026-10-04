package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Фоновый градиент экранов авторизации (аналог .auth background). */
fun Modifier.authBackground(): Modifier = this.background(
    Brush.verticalGradient(listOf(Color(0xFF1A1B1D), Color(0xFF1E1F21), Color(0xFF161719))),
)

@Composable
fun insetTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun insetBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
