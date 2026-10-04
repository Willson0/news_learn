#!/bin/sh
# Подготовка Laravel перед стартом: ключ, БД, миграции, кэши.
set -e
cd /app/backend

# Папки storage (том монтируется пустым при первом запуске)
mkdir -p storage/app/private storage/framework/cache/data storage/framework/sessions \
         storage/framework/views storage/logs database/data

# APP_KEY: если не задан в .env — генерируем один раз и храним в томе storage
if [ -z "$APP_KEY" ]; then
    if [ ! -f storage/app/.app_key ]; then
        echo "base64:$(head -c 32 /dev/urandom | base64)" > storage/app/.app_key
    fi
    export APP_KEY="$(cat storage/app/.app_key)"
fi

# SQLite: создаём файл и при первом запуске наполняем демо-данными
FIRST_RUN=0
if [ ! -f "$DB_DATABASE" ]; then
    touch "$DB_DATABASE"
    FIRST_RUN=1
fi

php artisan migrate --force
if [ "$FIRST_RUN" = "1" ] && [ "${SEED_DEMO_DATA:-true}" = "true" ]; then
    php artisan db:seed --force
fi

php artisan config:cache
php artisan route:cache
php artisan view:cache

exec "$@"
