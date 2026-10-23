package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Figma EASE_IN_AND_OUT_BACK из прототипа: оверлеи и попапы (dissolve 800 мс). */
val EaseInOutBack: Easing = CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f)

/** Figma EASE_OUT (300 мс): появление ошибок на экранах авторизации. */
val EaseOutFigma: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
