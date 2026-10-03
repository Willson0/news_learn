/*
 * Текущий пользователь и его права. Админка видна только админам:
 * флаг is_admin приходит с бэкенда (проверка по Telegram ID).
 */
import { reactive } from 'vue'
import { fetchMe } from './auth'
import { isAuthenticated } from './client'

export const session = reactive({
  user: null,
  loaded: false,
})

let pending = null

export function loadSession(force = false) {
  if (!isAuthenticated()) {
    session.user = null
    session.loaded = true
    return Promise.resolve(null)
  }
  if (pending && !force) return pending
  pending = fetchMe()
    .then((r) => {
      session.user = r.user
      return r.user
    })
    .catch(() => {
      session.user = null
      return null
    })
    .finally(() => {
      session.loaded = true
    })
  return pending
}

export function setSessionUser(user) {
  session.user = user
  session.loaded = true
  pending = Promise.resolve(user)
}

export function clearSession() {
  session.user = null
  pending = null
}

export function isAdmin() {
  return Boolean(session.user && session.user.is_admin)
}
