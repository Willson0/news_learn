# Android «Карта Ликвидности» — полное руководство: установка, тесты, деплой

Нативное приложение на **Kotlin + Jetpack Compose**, повторяющее веб-фронт
(`frontend/`) один-в-один и работающее с тем же **Laravel**-бэкендом (`backend/`)
по REST API. Бэкенд при этом не меняется — мобильный клиент лишь ещё один
потребитель того же API, что и веб.

Этот документ самодостаточный: он проведёт от чистой машины до работающего
приложения в эмуляторе и до релизного артефакта (APK/AAB) в магазине.

Содержание:
1. [Что потребуется](#1-что-потребуется)
2. [Поднять бэкенд (локально или на сервере)](#2-поднять-бэкенд)
3. [Указать адрес бэкенда в сборке (`API_BASE_URL`)](#3-адрес-бэкенда-api_base_url)
4. [Собрать debug‑APK](#4-собрать-debug-apk)
5. [Запустить в эмуляторе и протестировать](#5-запуск-в-эмуляторе-и-тестирование)
6. [Запуск на реальном телефоне](#6-запуск-на-реальном-телефоне)
7. [Релизная сборка: подпись, APK и AAB](#7-релизная-сборка-подпись-apk-и-aab)
8. [Публикация в Google Play](#8-публикация-в-google-play)
9. [Диагностика типичных проблем](#9-диагностика-типичных-проблем)

---

## 1. Что потребуется

| Инструмент | Зачем | Где взять |
| --- | --- | --- |
| **Android Studio** (последняя стабильная) | IDE, Android SDK, эмулятор, JDK — всё в одном | https://developer.android.com/studio |
| **JDK 17** | сборка (идёт внутри Android Studio) | — |
| Аппаратная виртуализация (VT‑x / AMD‑V) | быстрый эмулятор | включается в BIOS/UEFI |

Отдельно ставить Gradle/SDK не нужно — всё приедет при первом запуске Android
Studio. Параметры проекта: `minSdk 24` (Android 7.0), `targetSdk 35`,
Kotlin 2.0, Compose. То есть приложение запустится на любом устройстве/эмуляторе
с Android 7.0 и новее.

> **Проверка интерфейса без сборки.** Каждый прогон CI (GitHub Actions,
> workflow `.github/workflows/android.yml`) кладёт готовый `app-debug.apk` в
> артефакт сборки `app-debug`. Его можно скачать со страницы Actions и поставить
> в эмулятор без установки Android Studio — но он собран с заглушкой‑адресом,
> поэтому увидите только интерфейс, без живых данных. Для работы с данными
> собирайте сами с реальным `API_BASE_URL` (шаг 3).

---

## 2. Поднять бэкенд

Мобильному приложению нужен работающий REST API. Есть два пути.

### Вариант А. Локально на своём ПК (для разработки и тестов)

Нужны **PHP 8.2+** и **Composer**. Проще всего — через
[Laravel Herd](https://herd.laravel.com) (Windows/macOS, ставит PHP и Composer
сам) либо вручную.

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate --seed      # таблицы + демо-данные (отчёты, чаты, пользователь)
php artisan serve --host 0.0.0.0 --port 8000
```

- `--host 0.0.0.0` важен: иначе сервер слушает только `127.0.0.1` и эмулятор к
  нему не достучится.
- Проверка: в браузере `http://localhost:8000/api/ping` → `{"status":"ok",...}`.
- Демо‑вход в приложении: **`demo@karta.app` / `password`**.
- Для входа по коду из письма (регистрация, восстановление пароля) почта не
  обязательна — код всегда дублируется в лог Laravel
  (`backend/storage/logs/laravel.log`, строка «Код подтверждения»).

> **Вход через Telegram в мобильном приложении недоступен** — он работает только
> внутри Telegram (там есть подписанный `initData`). В нативном Android‑клиенте
> вход выполняется по email/телефону и паролю или через регистрацию. Кнопка
> «Войти с Yandex» на экране входа — заглушка дизайна.

### Вариант Б. На сервере по HTTPS (прод/стенд)

Полный продакшн‑деплой бэкенда (и веба) одним Docker‑контейнером с автоматическим
HTTPS описан в [`../deploy/README.md`](../deploy/README.md). Коротко: на VPS с
Docker и доменом — `docker compose -f deploy/docker-compose.yml up -d --build`,
после чего API доступен по `https://ваш-домен/api/`.

---

## 3. Адрес бэкенда (`API_BASE_URL`)

Адрес API «зашивается» в сборку параметром **`API_BASE_URL`**. Он
**обязан быть корректным URL и оканчиваться на `/api/`**. По умолчанию там
заглушка `https://karta.example.com/api/` — с ней данные грузиться не будут.

Задать свой адрес можно тремя способами (достаточно одного):

**Способ 1 — файл `android/gradle.properties`** (удобно для постоянной работы).
Добавьте строку:

```properties
# Бэкенд на сервере с HTTPS:
API_BASE_URL=https://ваш-домен/api/
```

```properties
# ИЛИ: локальный бэкенд (php artisan serve на этом же ПК, порт 8000).
# Из Android-эмулятора хост-компьютер виден как 10.0.2.2, НЕ localhost:
API_BASE_URL=http://10.0.2.2:8000/api/
```

**Способ 2 — флаг командной строки** (разово):

```bash
./gradlew assembleDebug -PAPI_BASE_URL=http://10.0.2.2:8000/api/
```

**Способ 3 — в Android Studio**: `gradle.properties` открывается прямо в дереве
проекта, правьте там же.

Важные нюансы:
- **`10.0.2.2` — это специальный алиас эмулятора для «машины‑хоста»**. `localhost`
  внутри эмулятора указывает на сам эмулятор, а не на ваш ПК.
- **Незашифрованный `http://` разрешён только в debug‑сборке** (см.
  `app/src/debug/AndroidManifest.xml`, `usesCleartextTraffic=true`). В release
  cleartext запрещён — прод‑бэкенд должен работать по `https://`.
- Приложение не упадёт, если адрес окажется недоступен: экраны мягко покажут
  пустое состояние/ошибку.

На стороне Laravel менять код не нужно. Если же API крутится на другом домене,
чем фронт, проверьте, что CORS (`config/cors.php` / `FRONTEND_URL`) не режет
запросы — для нативного клиента заголовка `Origin` нет, обычно всё работает.

---

## 4. Собрать debug‑APK

**Через Android Studio (проще всего):**
1. `File → Open` → выберите папку **`android`** из репозитория (именно её, не
   корень репозитория).
2. Дождитесь окончания Gradle sync (первый раз — несколько минут, качаются
   зависимости).
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. Готовый файл: `android/app/build/outputs/apk/debug/app-debug.apk`.

**Или из командной строки** (в папке `android`):
```bash
./gradlew assembleDebug          # Linux/macOS
gradlew.bat assembleDebug        # Windows
```

---

## 5. Запуск в эмуляторе и тестирование

### Создать и запустить эмулятор
1. В Android Studio: `Tools → Device Manager → Create Device`.
2. Выберите, например, **Pixel 7**, образ системы **Android 14 (x86_64)** →
   `Finish`.
3. Запустите эмулятор кнопкой ▶ в Device Manager.

### Установить и открыть приложение
- Нажмите зелёный **Run ▶** в Android Studio (с выбранным эмулятором) — IDE сама
  соберёт, установит и откроет приложение; **или**
- перетащите `app-debug.apk` в окно эмулятора; **или**
- `adb install android/app/build/outputs/apk/debug/app-debug.apk`.

### Что проверить (сценарий приёмки)
Поднимите локальный бэкенд (шаг 2, вариант А) с `API_BASE_URL=http://10.0.2.2:8000/api/`
и пройдите:

1. **Вход** под `demo@karta.app` / `password` → попадаем на главную.
2. **Регистрация**: новый email/телефон/пароль → экран ввода кода. Код возьмите
   из `backend/storage/logs/laravel.log` → подтверждение → вход.
3. **Главная**: лента материалов, переключение фильтра по инструменту.
4. **Аналитика**: поиск по заголовку, кнопка фильтров (по статусу), открытие
   отчёта, кнопки «Открыть отчёт» (html в WebView), «Открыть график».
5. **Сообщество**: список чатов, вход в чат, отправка сообщения (появляется в
   ленте).
6. **Профиль**: подписка, отслеживаемые инструменты (переключение сохраняется),
   «Данные аккаунта» → смена email/телефона/пароля, «Выйти из аккаунта».
7. **Админ‑функции** (если ваш аккаунт — админ, см. `backend/config/admin.php`):
   на экране аналитики появляется кнопка «+», создание/редактирование/удаление
   отчёта с загрузкой обложки и html, настройка чатов, список администраторов.

### Логи и отладка сети
```bash
adb logcat | grep -i okhttp     # в debug-сборке включён BASIC-лог запросов
```

---

## 6. Запуск на реальном телефоне

1. На телефоне включите **Режим разработчика** (Настройки → «О телефоне» → 7 раз
   тапнуть по «Номер сборки») и **Отладку по USB**.
2. Подключите телефон кабелем, разрешите отладку.
3. В Android Studio телефон появится в списке устройств — нажмите **Run ▶**.
4. Если бэкенд локальный, телефон `10.0.2.2` не видит. Варианты:
   - соберите с `API_BASE_URL=http://<IP-вашего-ПК-в-локальной-сети>:8000/api/`
     (телефон и ПК в одной Wi‑Fi‑сети; `php artisan serve --host 0.0.0.0`), **или**
   - используйте `adb reverse tcp:8000 tcp:8000` и адрес `http://localhost:8000/api/`
     (проброс порта ПК на телефон; это единственный случай, когда с реального
     устройства в debug подходит `localhost`).

---

## 7. Релизная сборка: подпись, APK и AAB

Debug‑APK подписан отладочным ключом и не годится для распространения. Для релиза
нужен **свой keystore** и подпись.

> В текущем проекте у `buildType release` подпись ещё не настроена, поэтому
> `assembleRelease` без настройки ниже даст **неподписанный** артефакт. Выберите
> один из двух путей.

### Путь А. Через мастер Android Studio (без правок Gradle, рекомендуется для первого раза)
1. `Build → Generate Signed Bundle / APK…`.
2. Выберите **Android App Bundle** (для Google Play) или **APK** (для ручной
   раздачи).
3. `Create new…` → задайте путь к keystore, пароли, alias — **сохраните их
   надёжно, восстановить нельзя**.
4. Build variant — **release**, подпись — ваш ключ → `Finish`.
5. Результат: `android/app/build/outputs/bundle/release/app-release.aab` или
   `.../apk/release/app-release.apk`.

> Не забудьте перед этим задать боевой `API_BASE_URL` (HTTPS!) в
> `gradle.properties` — релиз не умеет ходить по `http`.

### Путь Б. Автоматическая подпись в Gradle (для CI и повторяемых сборок)
1. Создайте keystore один раз:
   ```bash
   keytool -genkeypair -v -keystore karta-release.jks \
     -alias karta -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Положите секреты в `android/keystore.properties` (файл **не** коммитить, добавьте
   в `.gitignore`):
   ```properties
   storeFile=/абсолютный/путь/karta-release.jks
   storePassword=…
   keyAlias=karta
   keyPassword=…
   ```
3. В `android/app/build.gradle.kts` добавьте чтение файла и `signingConfig`:
   ```kotlin
   import java.util.Properties
   import java.io.FileInputStream

   val keystoreProps = Properties().apply {
       val f = rootProject.file("keystore.properties")
       if (f.exists()) load(FileInputStream(f))
   }

   android {
       signingConfigs {
           create("release") {
               if (keystoreProps.getProperty("storeFile") != null) {
                   storeFile = file(keystoreProps.getProperty("storeFile"))
                   storePassword = keystoreProps.getProperty("storePassword")
                   keyAlias = keystoreProps.getProperty("keyAlias")
                   keyPassword = keystoreProps.getProperty("keyPassword")
               }
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               // при желании включите минификацию:
               // isMinifyEnabled = true
           }
       }
   }
   ```
4. Собрать:
   ```bash
   ./gradlew bundleRelease   -PAPI_BASE_URL=https://ваш-домен/api/   # AAB для Play
   ./gradlew assembleRelease -PAPI_BASE_URL=https://ваш-домен/api/   # подписанный APK
   ```

**Когда что:** `.aab` — для загрузки в Google Play (магазин сам нарежет APK под
устройства). `.apk` — для ручной установки/раздачи вне магазина.

---

## 8. Публикация в Google Play

1. Аккаунт [Google Play Console](https://play.google.com/console) (разовый взнос).
2. Создайте приложение, заполните карточку (название, описание, иконка, скриншоты,
   политика конфиденциальности).
3. `Testing → Internal testing` (или Production) → загрузите `app-release.aab`.
4. При первой загрузке включите **Play App Signing** — Google будет держать ключ
   подписи приложения, а вы — ключ загрузки.
5. `applicationId` (`app.karta.likvidnosti`) должен быть уникальным в магазине; при
   необходимости поменяйте его в `app/build.gradle.kts`.
6. Поднимайте `versionCode` (целое, +1 на каждую загрузку) и `versionName` в
   `defaultConfig` перед каждым релизом.

---

## 9. Диагностика типичных проблем

| Симптом | Причина / решение |
| --- | --- |
| Данные не грузятся, везде пусто | Неверный/недоступный `API_BASE_URL`. Проверьте `/api/ping` из браузера; для эмулятора хост — `10.0.2.2`, не `localhost`. Бэкенд запущен с `--host 0.0.0.0`. |
| `CLEARTEXT communication not permitted` | Собрали **release** с `http://`. Для прод нужен `https://`; локально тестируйте debug‑сборкой. |
| Эмулятор очень медленный | Не включена аппаратная виртуализация в BIOS, или выбран ARM‑образ. Берите образ **x86_64**. |
| Gradle sync падает на загрузке зависимостей | Нет интернета/прокси. Нужен доступ к `dl.google.com` и Maven Central. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | На устройстве стоит сборка с другой подписью. Удалите старую: `adb uninstall app.karta.likvidnosti`. |
| Регистрация не приходит на почту | SMTP не настроен — это нормально для dev. Код лежит в `backend/storage/logs/laravel.log`. |

Сборка проверяется на CI при каждом пуше (`.github/workflows/android.yml`,
`./gradlew :app:assembleDebug`); ошибки компиляции выводятся отдельным блоком в
конце лога.
