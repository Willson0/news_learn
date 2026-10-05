# iOS-приложение «Карта Ликвидности»

Нативное приложение на **Swift + SwiftUI**, повторяющее веб-фронт
(`frontend/`) один-в-один и работающее с тем же Laravel-бэкендом (`backend/`)
по тому же REST API. Бэкенд не меняется.

## Структура

```
ios/
├── project.yml                 # Описание проекта для XcodeGen
└── KartaLikvidnosti/
    ├── App.swift               # Точка входа
    ├── Info.plist
    ├── Theme/                  # Дизайн-токены (перенос tokens.css)
    ├── Icons/                  # Движок SVG-иконок + реестр (1:1 с вебом)
    ├── Data/                   # API-клиент, модели, сессия, токен
    ├── Components/             # Кнопки, поля, карточки, график, чаты
    ├── Navigation/             # Router + корневой экран
    └── Screens/                # 20 экранов (вход … админка)
```

Xcode-проект **не хранится в репозитории** — он генерируется из
`project.yml` утилитой [XcodeGen](https://github.com/yonaskolb/XcodeGen).

## Сборка в Xcode (на Mac)

```bash
brew install xcodegen         # один раз
cd ios
xcodegen generate             # создаёт KartaLikvidnosti.xcodeproj
open KartaLikvidnosti.xcodeproj
```

Выберите схему **KartaLikvidnosti**, симулятор iPhone и нажмите **Run**.

## Адрес бэкенда

Базовый URL API задаётся параметром сборки `API_BASE_URL` (должен
оканчиваться на `/api/`). По умолчанию — `http://localhost:8000/api/`.

- **Локальный бэкенд в симуляторе:** хост Mac виден как обычный
  `localhost`/`127.0.0.1` (в отличие от Android-эмулятора, где это
  `10.0.2.2`). Запустите `php artisan serve` в `backend/` — приложение
  заработает с настройками по умолчанию.
- **Другой адрес** (прод, стенд):

  ```bash
  xcodebuild -project KartaLikvidnosti.xcodeproj -scheme KartaLikvidnosti \
    -sdk iphonesimulator API_BASE_URL='https://karta.example.com/api/' build
  ```

  либо задайте `API_BASE_URL` в настройках таргета (Build Settings) или в
  `project.yml` перед генерацией.

HTTP к `localhost` разрешён через `NSAllowsLocalNetworking` в `Info.plist`;
прод-бэкенд по HTTPS работает без дополнительных настроек.

## Сборка на CI

Локального Xcode в облачном окружении нет, поэтому сборка проверяется на
GitHub Actions (`.github/workflows/ios.yml`) на macOS-раннере: XcodeGen
генерирует проект, затем `xcodebuild` собирает debug под симулятор.
