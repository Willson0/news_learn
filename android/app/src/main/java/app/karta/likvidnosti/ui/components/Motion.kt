package app.karta.likvidnosti.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

// Анимации из прототипа Figma (файл MtvMQiOEz1qNOWzAl2deus, страница «Дизайн»):
// тип перехода, кривая и длительность взяты из реакций прототипа (iOS Motion.swift).

/** Figma EASE_IN_AND_OUT_BACK — cubic-bezier(0.7, -0.4, 0.4, 1.4). */
val EaseInOutBack: Easing = CubicBezierEasing(0.7f, -0.4f, 0.4f, 1.4f)

/** Figma EASE_OUT (300 мс): поля и ошибки на экранах авторизации. */
val EaseOutFigma: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** Figma EASE_IN_AND_OUT. */
val EaseInOutFigma: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Длительность SMART_ANIMATE / DISSOLVE прототипа: навбар, попапы, чипсы, свитчи. */
const val FigmaMs = 800

/** SMART_ANIMATE / DISSOLVE, EASE_IN_AND_OUT_BACK, 800 мс. */
fun <T> figmaTween(): FiniteAnimationSpec<T> = tween(FigmaMs, easing = EaseInOutBack)

/**
 * Figma SLOW — пружина (масса 1, жёсткость 80, затухание 20; ≈1.25 с):
 * раскрытие графика в отчёте, лист фильтров аналитики.
 */
fun <T> figmaSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 20f / (2f * 8.944272f), stiffness = 80f)

/**
 * Та же кривая для прозрачности: выброс ease-in-out-back за пределы 0…1 срезается
 * (как в Figma и SwiftUI — прозрачность не бывает меньше 0 и больше 1).
 */
val EaseInOutBackAlpha: Easing = Easing { EaseInOutBack.transform(it).coerceIn(0f, 1f) }

/** DISSOLVE прототипа: растворение 800 мс, ease-in-out-back. */
fun <T> figmaFade(): FiniteAnimationSpec<T> = tween(FigmaMs, easing = EaseInOutBackAlpha)

/**
 * Лента чата (как в Telegram): новое сообщение и сдвиг соседей — быстрая пружина без
 * выброса назад; у back-кривой первые ~200 мс почти нет движения, и отправка «подвисала».
 */
fun <T> chatSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)
