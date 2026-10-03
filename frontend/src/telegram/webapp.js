/*
 * Обёртка над Telegram Web App SDK (window.Telegram.WebApp).
 * SDK подключается скриптом в index.html. Вне Telegram (например, в обычном
 * браузере при разработке) объекта нет — тогда методы безопасно ничего не делают.
 */

const BG_COLOR = '#1a1b1d'

/** Возвращает объект Telegram.WebApp или null, если приложение открыто не в Telegram. */
export function getWebApp() {
  return (typeof window !== 'undefined' && window.Telegram && window.Telegram.WebApp) || null
}

/** true, если страница реально запущена внутри Telegram. */
export function isTelegram() {
  const wa = getWebApp()
  return Boolean(wa && wa.initData)
}

/**
 * Инициализация мини-приложения: сообщаем Telegram о готовности, разворачиваем
 * на весь экран и выставляем цвета фона/шапки под тёмную тему макета.
 */
export function initTelegram() {
  const wa = getWebApp()
  if (!wa) return

  try {
    wa.ready()
    wa.expand()
    if (typeof wa.setHeaderColor === 'function') wa.setHeaderColor(BG_COLOR)
    if (typeof wa.setBackgroundColor === 'function') wa.setBackgroundColor(BG_COLOR)
    if (typeof wa.disableVerticalSwipes === 'function') wa.disableVerticalSwipes()
  } catch (e) {
    // SDK может быть неполным в старых клиентах — не роняем приложение
    console.warn('[telegram] init failed', e)
  }
}

/** Лёгкая тактильная отдача (если клиент поддерживает). */
export function haptic(type = 'light') {
  const wa = getWebApp()
  if (wa && wa.HapticFeedback) {
    try {
      wa.HapticFeedback.impactOccurred(type)
    } catch {
      /* ignore */
    }
  }
}
