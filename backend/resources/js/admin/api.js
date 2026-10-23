import { reactive } from 'vue';

// Состояние входа: login заполняется после /me, checked — что проверка уже была.
export const session = reactive({ login: null, checked: false });

const BASE = '/admin/api';

function csrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}

function firstError(errors = {}) {
    const first = Object.values(errors)[0];
    return Array.isArray(first) ? first[0] : first;
}

/**
 * Запрос к API админки. Бросает ошибку с текстом сервера и полем errors для валидации.
 */
export async function api(method, path, body) {
    const isForm = body instanceof FormData;
    const headers = { Accept: 'application/json', 'X-XSRF-TOKEN': csrfToken() };
    if (body && !isForm) headers['Content-Type'] = 'application/json';

    const response = await fetch(`${BASE}${path}`, {
        method,
        headers,
        credentials: 'same-origin',
        body: isForm ? body : body ? JSON.stringify(body) : undefined,
    });

    if (response.status === 204) return null;
    const json = await response.json().catch(() => ({}));

    if (!response.ok) {
        const error = new Error(json.message || firstError(json.errors) || 'Не удалось выполнить запрос');
        error.status = response.status;
        error.errors = json.errors || {};
        throw error;
    }

    return json;
}

export function money(value) {
    return `${Number(value || 0).toLocaleString('ru-RU')} ₽`;
}
