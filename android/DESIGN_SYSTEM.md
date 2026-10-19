# Дизайн-система Android ↔ iOS

Android-версия повторяет iOS-редизайн (`ios/KartaLikvidnosti`, ветка `ios`) 1:1 по
макету Figma (страница «Дизайн»). Координаты макета — кадр 393×852 со статус-баром 54;
на Android y-координаты переводятся через `designTop(y)` (= статус-бар устройства + (y − 54)),
отступы от низа — через `designBottom(b)` / `navBarClearance()`.

## Токены и шрифты

| Android | iOS | Что |
|---|---|---|
| `ui/theme/Tokens.kt` → `T.*` | `Theme/Theme.swift` → `Theme.*` | цвета, градиенты, радиусы, отступы (`T.Surface` = `Theme.surface`, `T.TopBarY` = `Theme.topBarY` …) |
| `hex("#rrggbb")` | `Color(hex:)` | цвет из hex |
| `AppFont.heading(size)` / `AppFont.sans(size, weight)` | `Theme.heading` / `Theme.sans` | Kreadon Demi / Min Sans (`res/font/*.otf`) |
| `Modifier.screenBackground()` | `ScreenBackdrop` / `.screenBackground()` | градиент «Фон» + свечение `bg_glow` |
| `designTop`, `designBottom`, `navBarClearance` | абсолютные координаты макета | перевод координат |

## Стекло (`ui/components/Glass.kt`)

| Android | iOS |
|---|---|
| `Modifier.liquidGlass(shape, fill)` | `.liquidGlass(_:fill:)` (вариант ниже iOS 26) |
| `Fills.Glass / Active / Purple / Pink / NavBar / Black2 / Danger`, `GlassFill(color)` | `Theme.glassFill`, `ActiveFill`, `PurpleFill`, `PinkFill`, … |
| `CssLinearGradient` | `CSSLinearGradient` |
| `BackdropBox`, `LocalBackdrop`, `Modifier.backdropBlur(r)` | `BackdropBlur` |
| `Modifier.pressable { }` | `PressableStyle` |
| `GlassCircleButton`, `GlassBackButton`, `GlassLabel`, `GlassTopBar` | те же имена |
| `CapsuleShape` | `Capsule()` |

Стекло на Android — имитация: полупрозрачная заливка, мягкое внутреннее свечение кромки
и угловой блик-обводка 1dp (те же стопы, что `GlassRim` в iOS). Размытие подложки
работает на Android 12+ (API 31) там, где элемент лежит поверх контента, записанного в
`BackdropBox` (навбар, верхняя панель отчёта, затемнение листа фильтров/диалога). Элементы
на непрозрачных плашках подложку не получают (`LocalBackdrop = null`).

## Иконки (`ui/icons`)

| Android | iOS |
|---|---|
| `IconDef`, `IconPart`, `fp`, `sp`, `box` | `IconDef`, `IconPart`, `fp`, `sp` |
| `AppIcon(AppIcons.back, size, tint)` | `Icon(AppIcons.back, size:, color:)` |
| `AppIcons.<camelCase>` (navHome, back, settings, filter, chat, doc, check, checkboxOn, calendarNumber, matGold …) | `AppIcons.<name>` из `AppIcons.swift` + `FigmaIcons.swift` |
| `AppIcons.material(key)` | `AppIcons.material(_:)` |
| `AppIcons.<PascalCase>` (Int, drawable) + `AppIcon(res)` | — старые иконки, для ещё не перерисованных экранов |

## Компоненты (`ui/components`)

| Android | iOS |
|---|---|
| `AppButton(text, variant = Accent/Soft/Secondary/Danger)` | `AppButton` |
| `AppInput(value, onValueChange, placeholder, isPassword, error, label)` | `AppInput` (`isSecure` = `isPassword`) |
| `AppToggle` | `AppToggle` |
| `InstrumentChip(label, checked, onToggle, hPadding)` | `InstrumentChip` |
| `SegmentedControl(value, options, onSelect, glass)` | `SegmentedControl` |
| `SelectionLens` | `SelectionLens` |
| `ReportActionButton(label, icon, variant = Accent/Pink/ChartOff/ChartOn)` | `ReportActionButton` |
| `PillActionButton`, `PurplePillButton` | те же |
| `ScreenTitle` | `Text(...).font(Theme.heading(24))` |
| `GlassNavBar(current, onSelect, extra)`, `NavBarExtra`, `AppTab`, `TabScaffold` | `GlassNavBar`, `GlassNavBar.Extra`, `Tab`, `.withGlassNavBar()` |
| `AvatarCircle`, `RemoteImage`, `HoloFoil`, `HairlineDivider` | `AvatarCircle`, `RemoteImage`, `HoloFoil` |
| `ReportCard`, `ReportCover`, `ReportCoverPlaceholder` (`drawable/report_cover.jpg`) | `ReportCard`, `ReportCoverPlaceholder` |
| `ReportCardSkeleton`, `ChatRowSkeleton`, `SkeletonBox` | `ReportCardSkeleton`, `ChatRowSkeleton`, `SkeletonBlock` |
| `MaterialChips` | `MaterialChips` |
| `ConfirmDialog(open, …)` | `ConfirmDialog` |
| `AuthScaffold`, `AuthField`, `AuthFieldError`, `AuthMessageView` | те же |
| `InnerScreen(onBack, trailing, overlay)` — прокрутка + `GlassTopBar`, заголовок на y = 160 | `.glassTopBar()` внутренних экранов |
| `PhoneMask` (`ui/screens/LoginScreen.kt`) | `PhoneMask` |
| `FlowLayout` | `FlowLayout` |
| `AppCalendar(start, end, onChange)`, `calendarPick` | `AppCalendar` |
| `FiltersSheet`, `FiltersOverlay`, `FiltersResult` | те же |
| `CandlestickChart` | `CandlestickChart` |
| `Screen`, `ScreenHeader`, `BackButton`, `FieldError` | — старый каркас (для ещё не перерисованных экранов, внешне уже на новых токенах) |

## Сессия

`Session.subscriptionActive` и `Session.restricted` (пользователь загружен, не админ,
подписка есть и `active == false`) — как в iOS `Data/Session.swift`. Без подписки навбар
120 (только «Сообщество» и «Профиль»), главная и аналитика перенаправляют в «Сообщество».
