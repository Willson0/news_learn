# Карта Ликвидности

Telegram Web App по дизайну из Figma. Репозиторий состоит из двух независимых
приложений, которые запускаются отдельными серверами:

- **`frontend/`** — Vue 3 (Options API) + Vite. Интерфейс Telegram Web App.
- **`backend/`** — Laravel 13. REST API (отдельный сервер).

## Быстрый старт

### Бэкенд (Laravel)

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate
php artisan serve        # http://localhost:8000
```

Проверка: `GET http://localhost:8000/api/ping`.

### Фронтенд (Vue)

```bash
cd frontend
npm install
npm run dev              # http://localhost:5173
```

В dev-режиме запросы к `/api` проксируются на бэкенд (`vite.config.js`). Разрешённый
источник для CORS задаётся переменной `FRONTEND_URL` в `backend/.env`.

## Экраны

Вёрстка идёт по странице «Дизайн» в Figma, по одному экрану на PR:

1. Вход и регистрация
2. Восстановление пароля
3. Главная
4. Аналитика отчёты
5. Сообщество
6. Профиль

Дизайн-токены (цвета, шрифты, радиусы) вынесены в `frontend/src/styles/tokens.css`.

## Структура

```
.
├── backend/    # Laravel API
├── frontend/   # Vue 3 + Vite
└── README.md
```
