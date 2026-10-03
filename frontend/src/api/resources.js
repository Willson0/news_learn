/*
 * Запросы к ресурсам API: отчёты, инструменты, профиль, подписка, чаты.
 */
import { api } from './client'

// --- Отчёты / материалы ---
export function fetchReports({ instrument, search, status } = {}) {
  const params = new URLSearchParams()
  if (instrument && instrument !== 'all') params.set('instrument', instrument)
  if (search) params.set('search', search)
  if (status) params.set('status', Array.isArray(status) ? status.join(',') : status)
  const qs = params.toString()
  return api.get(`/reports${qs ? `?${qs}` : ''}`).then((r) => r.data)
}

export function fetchReport(id) {
  return api.get(`/reports/${id}`).then((r) => r.data)
}

export function fetchInstruments() {
  return api.get('/instruments').then((r) => r.data)
}

// --- Профиль ---
export function fetchProfile() {
  return api.get('/profile')
}

export function updateProfile(payload) {
  return api.put('/profile', payload)
}

export function syncInstruments(keys) {
  return api.put('/profile/instruments', { instruments: keys })
}

export function updateNotifications(payload) {
  return api.put('/profile/notifications', payload)
}

// --- Подписка ---
export function fetchSubscription() {
  return api.get('/subscription').then((r) => r.data)
}

export function updateSubscription(payload) {
  return api.put('/subscription', payload)
}

// --- Данные аккаунта ---
export function changeEmail(email) {
  return api.put('/account/email', { email })
}

export function changePhone(phone) {
  return api.put('/account/phone', { phone })
}

export function changePassword(password, passwordConfirmation, currentPassword) {
  return api.put('/account/password', {
    password,
    password_confirmation: passwordConfirmation,
    current_password: currentPassword,
  })
}

// --- Сообщество ---
export function fetchChats() {
  return api.get('/chats').then((r) => r.data)
}

export function fetchMessages(slug) {
  return api.get(`/chats/${slug}/messages`)
}

export function sendMessage(slug, body) {
  return api.post(`/chats/${slug}/messages`, { body }).then((r) => r.data)
}

// --- Админка (только для админов) ---
function reportForm(payload) {
  const form = new FormData()
  form.append('title', payload.title || '')
  form.append('description', payload.description || '')
  form.append('chart_url', payload.chartUrl || '')
  form.append('instrument', payload.instrument || '')
  if (payload.cover) form.append('cover', payload.cover)
  if (payload.html) form.append('html', payload.html)
  if (payload.removeCover) form.append('remove_cover', '1')
  if (payload.removeHtml) form.append('remove_html', '1')
  return form
}

export function createReport(payload) {
  return api.post('/admin/reports', reportForm(payload)).then((r) => r.data)
}

export function updateReport(id, payload) {
  return api.post(`/admin/reports/${id}`, reportForm(payload)).then((r) => r.data)
}

export function deleteReport(id) {
  return api.del(`/admin/reports/${id}`)
}

export function updateChat(slug, { title, avatar }) {
  const form = new FormData()
  form.append('title', title || '')
  if (avatar) form.append('avatar', avatar)
  return api.post(`/admin/chats/${slug}`, form).then((r) => r.data)
}

export function fetchAdmins() {
  return api.get('/admin/admins').then((r) => r.data)
}

export function addAdmin(telegramId, name) {
  return api.post('/admin/admins', { telegram_id: telegramId, name }).then((r) => r.data)
}

export function removeAdmin(telegramId) {
  return api.del(`/admin/admins/${telegramId}`)
}
