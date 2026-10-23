# Продакшн-образ «Карта Ликвидности»: Vue-фронтенд + Laravel API в одном
# контейнере на FrankenPHP (Caddy + PHP). Caddy сам получает HTTPS-сертификат.
# Собирается из корня репозитория: docker compose -f deploy/docker-compose.yml build

# 1. Сборка фронтенда
FROM node:22-alpine AS frontend
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Сборка ассетов веб-админки (/admin, Vue)
FROM node:22-alpine AS backend-assets
WORKDIR /src
COPY backend/package.json backend/package-lock.json ./
RUN npm ci
COPY backend/ ./
RUN npm run build

# 3. Зависимости бэкенда
FROM composer:2 AS vendor
WORKDIR /src
COPY backend/composer.json backend/composer.lock ./
RUN composer install --no-dev --no-scripts --no-autoloader --prefer-dist --ignore-platform-reqs
COPY backend/ ./
RUN composer dump-autoload --optimize --no-dev --classmap-authoritative

# 4. Рантайм
FROM dunglas/frankenphp:1-php8.3
# pdo_sqlite и opcache уже есть в базовом образе

WORKDIR /app
COPY --from=vendor /src /app/backend
COPY --from=frontend /src/dist /app/frontend
COPY --from=backend-assets /src/public/build /app/backend/public/build
COPY deploy/Caddyfile /etc/frankenphp/Caddyfile
COPY deploy/entrypoint.sh /usr/local/bin/app-entrypoint
RUN chmod +x /usr/local/bin/app-entrypoint \
    && rm -f /app/backend/.env \
    && cp "$PHP_INI_DIR/php.ini-production" "$PHP_INI_DIR/php.ini" \
    && printf 'upload_max_filesize=100M\npost_max_size=105M\n' > "$PHP_INI_DIR/conf.d/uploads.ini"

# Логи пишутся и в файл storage/logs/laravel.log (том storage), и в stderr (docker compose logs).
ENV APP_ENV=production APP_DEBUG=false LOG_CHANNEL=stack LOG_STACK=single,stderr \
    DB_CONNECTION=sqlite DB_DATABASE=/app/backend/database/data/database.sqlite

EXPOSE 80 443 443/udp
ENTRYPOINT ["app-entrypoint"]
CMD ["frankenphp", "run", "--config", "/etc/frankenphp/Caddyfile"]
