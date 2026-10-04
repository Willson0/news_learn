# Android-приложение (Capacitor)

Нативное Android-приложение собирается из того же Vue-фронта, что и веб-версия,
с помощью [Capacitor](https://capacitorjs.com/). Веб-ассеты собираются Vite и
**вшиваются внутрь APK** (не грузятся с удалённого URL), а приложение получает
нативную оболочку: статус-бар, splash-экран, аппаратную кнопку «Назад» и
тактильную отдачу. Бэкенд при этом не меняется — приложение ходит в тот же
REST API Laravel.

## Что нужно установить

- **Node.js 18+** (сборка веб-части)
- **JDK 21**
- **Android Studio** (SDK, Platform-Tools, эмулятор или устройство)

После установки Android Studio задайте путь к SDK — Capacitor создаст
`android/local.properties` при первом открытии, либо пропишите вручную:

```
# frontend/android/local.properties
sdk.dir=/путь/к/Android/sdk
```

## Настройка адреса бэкенда

В вебе фронт и API живут на одном домене, поэтому там используется
относительный путь `/api`. В нативном приложении страница открывается с
локальной схемы (`https://localhost`), поэтому API нужно указать **абсолютным
URL** до развёрнутого бэкенда.

Отредактируйте `frontend/.env.capacitor`:

```
VITE_API_BASE=https://ваш-бэкенд.example.com/api
```

> **CORS.** Бэкенд должен разрешать обращения с источника нативного WebView.
> В `backend/.env` добавьте этот источник в `FRONTEND_URL`
> (для Android это `https://localhost`). Код бэкенда менять не нужно — только
> переменную окружения.

## Команды

Запускать из каталога `frontend/`:

```bash
npm install                 # один раз

npm run build:android       # собрать веб-ассеты в режиме capacitor (.env.capacitor)
npm run sync:android        # build:android + скопировать ассеты в android/ (cap sync)
npm run open:android        # открыть проект в Android Studio
npm run run:android         # собрать и запустить на устройстве/эмуляторе
```

Типичный цикл: поправили фронт → `npm run sync:android` → собрали/запустили
в Android Studio.

## Сборка APK / AAB вручную

После `npm run sync:android`:

```bash
cd android
./gradlew assembleDebug      # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease    # release APK (нужна подпись)
./gradlew bundleRelease      # AAB для Google Play
```

Для релиза настройте ключ подписи (`keystore`) в `android/app/build.gradle`
по [инструкции Android](https://developer.android.com/studio/publish/app-signing).

## Как устроена нативная интеграция

Фронт не переписан — изменения точечные и не трогают ни один экран:

- `capacitor.config.json` — конфигурация приложения (appId, splash, статус-бар).
- `src/native/capacitor.js` — инициализация нативной оболочки (статус-бар,
  splash, аппаратная кнопка «Назад» → роутер, нативные haptics и внешние ссылки).
- `src/native/backState.js` — общее состояние кнопки «Назад» (его использует и
  шапка Telegram, и аппаратная кнопка Android).
- `src/telegram/webapp.js` — существующая абстракция: вне Telegram `haptic()` и
  `openLink()` автоматически используют нативные плагины Capacitor.

В обычном браузере все нативные вызовы безопасно бездействуют, поэтому веб-версия
(в т.ч. как Telegram Web App) работает как раньше.
