/*
 * Нативный слой для Android-приложения (Capacitor).
 *
 * Фронт — тот же Vue-код, что и в вебе. Этот модуль добавляет нативное
 * поведение, когда приложение запущено как Android-приложение:
 *   - цвет и стиль статус-бара под тёмную тему макета;
 *   - скрытие нативного splash-экрана после загрузки;
 *   - аппаратная кнопка «Назад» → навигация роутера (как в Telegram);
 *   - тактильная отдача и открытие внешних ссылок через нативные плагины.
 *
 * В обычном браузере (веб-сборка) Capacitor.isNativePlatform() === false,
 * поэтому все функции безопасно бездействуют и ничего не ломают.
 */

import { Capacitor } from '@capacitor/core'
import { App } from '@capacitor/app'
import { StatusBar, Style } from '@capacitor/status-bar'
import { SplashScreen } from '@capacitor/splash-screen'
import { Haptics, ImpactStyle } from '@capacitor/haptics'
import { Browser } from '@capacitor/browser'

import { getBackState } from '@/native/backState'

const BG_COLOR = '#1a1b1d'

/** true, если приложение запущено как нативное (Android/iOS), а не в браузере. */
export function isNative() {
  try {
    return Capacitor.isNativePlatform()
  } catch {
    return false
  }
}

/** Сопоставление «телеграмных» типов отдачи со стилями Capacitor. */
function impactStyle(type) {
  switch (type) {
    case 'heavy':
    case 'rigid':
      return ImpactStyle.Heavy
    case 'medium':
      return ImpactStyle.Medium
    default:
      return ImpactStyle.Light
  }
}

/** Нативная тактильная отдача (fallback для haptic() вне Telegram). */
export function nativeHaptic(type = 'light') {
  if (!isNative()) return
  try {
    Haptics.impact({ style: impactStyle(type) })
  } catch {
    /* ignore */
  }
}

/** Открыть внешнюю ссылку в системном браузере (fallback для openLink()). */
export function nativeOpenLink(url) {
  if (!isNative() || !url) return false
  try {
    Browser.open({ url })
    return true
  } catch {
    return false
  }
}

/**
 * Инициализация нативной оболочки. Вызывается один раз из main.js.
 * Принимает vue-router, чтобы аппаратная кнопка «Назад» использовала ту же
 * логику, что и шапка Telegram (см. setBackButton в telegram/webapp.js).
 */
export async function initNative(router) {
  if (!isNative()) return

  // Статус-бар под тёмный фон макета.
  try {
    await StatusBar.setStyle({ style: Style.Dark })
    if (Capacitor.getPlatform() === 'android') {
      await StatusBar.setBackgroundColor({ color: BG_COLOR })
    }
  } catch {
    /* статус-бар недоступен — не роняем приложение */
  }

  // Аппаратная кнопка «Назад»: повторяем поведение Telegram BackButton.
  try {
    App.addListener('backButton', ({ canGoBack }) => {
      const state = getBackState()
      if (state && state.canGoBack && typeof state.onBack === 'function') {
        nativeHaptic('light')
        state.onBack()
      } else if (canGoBack) {
        router.back()
      } else {
        App.exitApp()
      }
    })
  } catch {
    /* ignore */
  }

  // Прячем splash после того, как Vue смонтировался и роутер готов.
  try {
    await router.isReady()
  } catch {
    /* ignore */
  }
  try {
    await SplashScreen.hide()
  } catch {
    /* ignore */
  }
}
