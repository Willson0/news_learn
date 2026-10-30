package app.karta.likvidnosti.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Тап по «пустому» месту экрана убирает клавиатуру (как в iOS-приложениях).
 *
 * Смотрим события в последнем проходе, после всех дочерних элементов: если тап
 * поймала кнопка, поле ввода, сообщение — он помечен как обработанный, и
 * клавиатура остаётся (например, «Отправить» в чате не прячет её). Прокрутку
 * и мультитач тапом не считаем.
 */
fun Modifier.dismissKeyboardOnBackgroundTap(onDismiss: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        val slop = viewConfiguration.touchSlop
        var tap = true
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.changes.size > 1) tap = false
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > slop) tap = false
            if (!change.pressed) {
                if (tap && !change.isConsumed) onDismiss()
                break
            }
            if (change.isConsumed) tap = false
        }
    }
}
