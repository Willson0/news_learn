/*
 * Небольшой клиент для REST API бэкенда (Laravel).
 * Токен Sanctum хранится в localStorage и добавляется в заголовок Authorization.
 * В dev-режиме запросы к /api проксируются на бэкенд (см. vite.config.js);
 * базовый URL можно переопределить через VITE_API_BASE.
 */

const BASE = import.meta.env.VITE_API_BASE || '/api'
const TOKEN_KEY = 'kl_token'

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || null
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* ignore */
  }
}

export function isAuthenticated() {
  return Boolean(getToken())
}

/**
 * Ошибка API: несёт http-статус и поля валидации (errors) от Laravel.
 */
export class ApiError extends Error {
  constructor(message, status, errors) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errors = errors || {}
  }
}

/**
 * Полный URL файла, который бэкенд отдаёт относительной ссылкой (обложка, html-отчёт, фото чата).
 */
export function fileUrl(path) {
  if (!path) return null
  if (/^https?:\/\//.test(path)) return path
  return `${BASE}${path}`
}

async function request(method, path, body) {
  const headers = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const options = { method, headers }
  if (body instanceof FormData) {
    // multipart: Content-Type с boundary браузер выставит сам
    options.body = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  let response
  try {
    response = await fetch(`${BASE}${path}`, options)
  } catch {
    throw new ApiError('Нет связи с сервером', 0)
  }

  if (response.status === 401) {
    setToken(null)
  }

  let data = null
  const text = await response.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }

  if (!response.ok) {
    const message = (data && data.message) || `Ошибка запроса (${response.status})`
    throw new ApiError(message, response.status, data && data.errors)
  }

  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path),
}
