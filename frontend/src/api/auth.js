/*
 * Методы авторизации поверх общего клиента.
 */
import { api, setToken } from './client'
import { getWebApp } from '@/telegram/webapp'
import { setSessionUser, clearSession } from './session'

export async function loginWithPassword(identifier, password) {
  const data = await api.post('/auth/login', { identifier, password })
  setToken(data.token)
  setSessionUser(data.user)
  return data.user
}

export async function register({ email, phone, password, name }) {
  const data = await api.post('/auth/register', { email, phone, password, name })
  setToken(data.token)
  setSessionUser(data.user)
  return data.user
}

export async function requestRecovery(email) {
  return api.post('/auth/recovery', { email })
}

export async function resetPassword(email, password) {
  const data = await api.post('/auth/recovery/reset', { email, password })
  setToken(data.token)
  setSessionUser(data.user)
  return data.user
}

/**
 * Авторизация через Telegram Web App: отправляем initData на проверку подписи.
 * Возвращает пользователя или null, если приложение открыто не в Telegram.
 */
export async function loginWithTelegram() {
  const wa = getWebApp()
  const initData = wa && wa.initData
  if (!initData) return null
  const data = await api.post('/auth/telegram', { init_data: initData })
  setToken(data.token)
  setSessionUser(data.user)
  return data.user
}

export async function logout() {
  try {
    await api.post('/auth/logout')
  } finally {
    setToken(null)
    clearSession()
  }
}

export function fetchMe() {
  return api.get('/user')
}
