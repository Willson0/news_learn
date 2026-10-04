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
php artisan migrate --seed   # создаст таблицы и демо-данные
php artisan serve           # http://localhost:8000
```

Проверка: `GET http://localhost:8000/api/ping`.

Демо-пользователь для входа по логину/паролю: `demo@karta.app` / `password`.

### REST API

Авторизация — токены Laravel Sanctum (`Authorization: Bearer <token>`).

| Метод | Путь | Назначение |
| --- | --- | --- |
| POST | `/api/auth/telegram` | Вход через Telegram Web App (проверка подписи `initData`) |
| POST | `/api/auth/register` | Регистрация по email, телефону и паролю |
| POST | `/api/auth/login` | Вход по email/телефону и паролю |
| POST | `/api/auth/recovery` | Запрос временного кода на почту |
| POST | `/api/auth/recovery/reset` | Установка нового пароля |
| POST | `/api/auth/logout` | Выход (удаление токена) |
| GET | `/api/user` | Текущий пользователь |
| GET | `/api/instruments` | Справочник инструментов |
| GET | `/api/reports` | Отчёты (`?instrument=`, `?search=`, `?status=`) |
| GET | `/api/reports/{id}` | Детальный отчёт |
| GET / PUT | `/api/profile` | Профиль / его изменение |
| PUT | `/api/profile/instruments` | Отслеживаемые инструменты |
| PUT | `/api/profile/notifications` | Настройки уведомлений |
| GET / PUT | `/api/subscription` | Подписка / автоплатёж |
| PUT | `/api/account/email` · `/phone` · `/password` | Данные аккаунта |
| GET | `/api/chats` | Список чатов по секциям |
| GET / POST | `/api/chats/{slug}/messages` | Сообщения чата / отправка |

#### Telegram Web App

`TELEGRAM_BOT_TOKEN` в `backend/.env` — токен бота; по нему проверяется подпись
`initData` мини-приложения (см. `app/Services/TelegramInitData.php`). Для локальной
отладки без токена можно включить `TELEGRAM_ALLOW_INSECURE=true` — тогда данные
Telegram принимаются без проверки подписи (только для разработки).

### Фронтенд (Vue)

```bash
cd frontend
npm install
npm run dev              # http://localhost:5173
```

В dev-режиме запросы к `/api` проксируются на бэкенд (`vite.config.js`). Разрешённый
источник для CORS задаётся переменной `FRONTEND_URL` в `backend/.env`.

## Деплой

Продакшн-запуск одним Docker-контейнером с автоматическим HTTPS — см.
[deploy/README.md](deploy/README.md).

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
├── deploy/     # Docker + Caddy для продакшна
└── README.md
```
