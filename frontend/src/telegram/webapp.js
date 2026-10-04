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

/**
 * Данные Telegram-пользователя из initDataUnsafe (id, first_name, last_name,
 * username, photo_url). Возвращает null, если открыто не в Telegram.
 */
export function getTelegramUser() {
  const wa = getWebApp()
  const user = wa && wa.initDataUnsafe && wa.initDataUnsafe.user
  return user && user.id ? user : null
}

let backHandler = null

/**
 * Управление системной кнопкой «Назад» в шапке Telegram.
 * Если canGoBack=true — показываем кнопку и вешаем обработчик; иначе прячем,
 * и Telegram сам показывает кнопку закрытия мини-приложения.
 */
export function setBackButton(canGoBack, onBack) {
  const wa = getWebApp()
  if (!wa || !wa.BackButton) return

  try {
    // Снимаем предыдущий обработчик, чтобы не копились подписки.
    if (backHandler && typeof wa.BackButton.offClick === 'function') {
      wa.BackButton.offClick(backHandler)
    }
    backHandler = null

    if (canGoBack) {
      backHandler = () => {
        haptic('light')
        if (typeof onBack === 'function') onBack()
      }
      if (typeof wa.BackButton.onClick === 'function') wa.BackButton.onClick(backHandler)
      if (typeof wa.BackButton.show === 'function') wa.BackButton.show()
    } else if (typeof wa.BackButton.hide === 'function') {
      wa.BackButton.hide()
    }
  } catch (e) {
    console.warn('[telegram] back button failed', e)
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

/** Открыть внешнюю ссылку: в Telegram — через openLink, в браузере — в новой вкладке. */
export function openLink(url) {
  const wa = getWebApp()
  if (wa && typeof wa.openLink === 'function' && wa.initData) {
    wa.openLink(url)
  } else {
    window.open(url, '_blank', 'noopener')
  }
}
