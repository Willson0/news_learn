/*
 * Небольшое общее хранилище состояния кнопки «Назад».
 *
 * Роутер через setBackButton() (telegram/webapp.js) сообщает, можно ли сейчас
 * вернуться назад и что для этого сделать. И шапка Telegram, и аппаратная
 * кнопка «Назад» в Android используют одно и то же состояние — так поведение
 * навигации одинаково на всех платформах.
 *
 * Модуль намеренно без зависимостей, чтобы не создавать циклических импортов
 * между telegram/webapp.js и native/capacitor.js.
 */

let state = { canGoBack: false, onBack: null }

export function setBackState(canGoBack, onBack) {
  state = { canGoBack: Boolean(canGoBack), onBack: onBack || null }
}

export function getBackState() {
  return state
}
