# Деплой на сервер

Всё приложение (Vue-фронтенд + Laravel API) запускается одним Docker-контейнером
на [FrankenPHP](https://frankenphp.dev) — это веб-сервер Caddy со встроенным PHP.
Caddy сам выпускает и продлевает HTTPS-сертификат Let's Encrypt, поэтому nginx и
certbot настраивать не нужно.

```
https://ваш-домен/          → Vue-приложение (frontend/dist)
https://ваш-домен/api/...   → Laravel API
```

Фронтенд и API живут на одном домене, поэтому CORS не мешает, а Telegram видит
обычный HTTPS-адрес.

## Что нужно

- VPS с Ubuntu 22.04/24.04 (хватит 1 ГБ RAM), открытые порты 80 и 443.
- Домен или поддомен, A-запись которого указывает на IP сервера.
  Если домена нет, подойдёт бесплатный адрес вида `1-2-3-4.sslip.io`
  (где `1-2-3-4` — IP сервера через дефисы): он уже указывает на ваш IP,
  и сертификат на него выпускается.
- Бот в Telegram и его токен от [@BotFather](https://t.me/BotFather).

Порты 80/443 не должны быть заняты другим веб-сервером (nginx, apache).

## 1. Установить Docker

```bash
curl -fsSL https://get.docker.com | sh
```

## 2. Скачать проект

```bash
git clone -b claude/project-thread-bp83ep https://github.com/Willson0/news_learn.git
cd news_learn
```

Если репозиторий приватный, git попросит логин и пароль: в качестве пароля
используйте Personal Access Token с GitHub (Settings → Developer settings →
Personal access tokens).

## 3. Заполнить настройки

```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```

Обязательно поменять:

| Переменная | Что указать |
| --- | --- |
| `DOMAIN` | домен без `https://`, например `app.example.ru` |
| `APP_URL`, `FRONTEND_URL` | тот же домен с `https://` |
| `TELEGRAM_BOT_TOKEN` | токен бота от @BotFather |

`APP_KEY` оставьте пустым: он сгенерируется при первом запуске и сохранится.

## 4. Запустить

```bash
docker compose -f deploy/docker-compose.yml up -d --build
```

При первом запуске контейнер создаст базу SQLite, выполнит миграции и заполнит
её демо-данными (отчёты, чаты, пользователь `demo@karta.app` / `password`).

Проверка: откройте `https://ваш-домен/api/ping` — должен вернуться
`{"status":"ok",...}`, а `https://ваш-домен/` должен открыть приложение.

## 5. Подключить к боту

В [@BotFather](https://t.me/BotFather):

1. `/mybots` → ваш бот → **Bot Settings** → **Menu Button** → **Configure menu button**,
   отправьте `https://ваш-домен` и название кнопки. Кнопка появится слева от поля ввода в чате с ботом.
2. По желанию: `/newapp` — создаёт прямую ссылку вида `t.me/ваш_бот/app`,
   указывайте тот же URL.

Открывайте приложение из Telegram: вход через Telegram работает только внутри
него (подпись `initData` проверяется по `TELEGRAM_BOT_TOKEN`).

## Админ-панель

Главный администратор задан в `backend/config/admin.php` (`root_telegram_id`).
Если это не ваш Telegram ID, поменяйте значение и пересоберите контейнер.
Остальных админов главный добавляет из профиля в приложении.

## Обновление

```bash
cd news_learn
git pull
docker compose -f deploy/docker-compose.yml up -d --build
```

База, загруженные файлы и сертификаты лежат в Docker-томах и при обновлении
сохраняются.

## Полезные команды

```bash
# логи
docker compose -f deploy/docker-compose.yml logs -f

# artisan внутри контейнера
docker compose -f deploy/docker-compose.yml exec app php artisan <команда>

# резервная копия базы
docker compose -f deploy/docker-compose.yml cp app:/app/backend/database/data/database.sqlite ./backup.sqlite
```

## Проверка без домена

Чтобы посмотреть сборку по IP без HTTPS, поставьте `DOMAIN=:80` и
`APP_URL`/`FRONTEND_URL=http://IP-сервера`. Внутри Telegram так работать не
будет: Telegram открывает Web App только по HTTPS.
