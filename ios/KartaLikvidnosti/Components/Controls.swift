import SwiftUI

// Базовые элементы управления — 1:1 с UI-китом макета (Button/default,
// input/Строка ввода, Button/Switch, чипы, Button/в отчете …). Все они в макете
// стеклянные (эффект «glass»).

enum AppButtonVariant { case accent, soft, secondary, danger }

/// Button/default: 48 pt, скругление 24, Min Sans 14, стекло.
/// accent/soft — заливка «active»; secondary — «Second color» 20%; danger — «red».
struct AppButton: View {
    let title: String
    var variant: AppButtonVariant = .accent
    var disabled: Bool = false
    /// Запрос выполняется: вместо подписи крутится индикатор, нажатие не срабатывает.
    var loading: Bool = false
    let action: () -> Void

    private let shape = RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous)

    var body: some View {
        Button(action: { if !disabled && !loading { action() } }) {
            Group {
                if loading {
                    ProgressView().tint(Theme.text)
                } else {
                    Text(title)
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .contentShape(shape)
            .modifier(Fill(variant: variant, shape: shape))
        }
        .buttonStyle(PressableStyle())
        .opacity(disabled ? 0.5 : 1)
        .disabled(disabled || loading)
    }

    private struct Fill: ViewModifier {
        let variant: AppButtonVariant
        let shape: RoundedRectangle
        func body(content: Content) -> some View {
            switch variant {
            case .accent, .soft: content.liquidGlass(shape, fill: ActiveFill())
            case .secondary: content.liquidGlass(shape)
            case .danger: content.liquidGlass(shape, fill: Theme.danger)
            }
        }
    }
}

/// input/Строка ввода: стекло, 48 pt, отступы 16, Min Sans 14.
/// С `label` — вариант «Строка ввода изменение чата» (64 pt, подпись 12 сверху).
struct AppInput: View {
    @Binding var text: String
    var placeholder: String = ""
    var label: String? = nil
    var isSecure: Bool = false
    var error: Bool = false
    var keyboard: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil
    var height: CGFloat? = nil

    @State private var revealed = false

    var body: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                if let label {
                    Text(label)
                        .font(Theme.sans(Theme.fontSm))
                        .foregroundStyle(Theme.textSecondary)
                }
                field
            }
            if isSecure {
                Button {
                    revealed.toggle()
                } label: {
                    Icon(revealed ? AppIcons.eye : AppIcons.eyeClosed, size: 24, color: Theme.text)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, isSecure ? 16 : 12)
        .frame(height: height ?? (label == nil ? 48 : 64))
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
    }

    @ViewBuilder private var field: some View {
        Group {
            if isSecure && !revealed {
                SecureField("", text: $text, prompt: prompt)
            } else {
                TextField("", text: $text, prompt: prompt)
                    .keyboardType(keyboard)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            }
        }
        .font(Theme.sans(Theme.fontBase))
        .foregroundStyle(error ? Theme.error : Theme.text)
        .textContentType(textContentType)
        .tint(Theme.green)
    }

    private var prompt: Text {
        Text(placeholder).foregroundColor(Theme.textMuted)
    }
}

/// Button/Switch: капсула 50×30 со стеклом. Вкл — заливка «active» и белый
/// кружок справа; выкл — «Second color» 20% и зелёный кружок (#629F00) слева.
struct AppToggle: View {
    @Binding var isOn: Bool
    var disabled: Bool = false

    var body: some View {
        Button {
            if !disabled { Haptics.light(); isOn.toggle() }
        } label: {
            ZStack(alignment: isOn ? .trailing : .leading) {
                Color.clear
                    .frame(width: 50, height: 30)
                    .modifier(Track(isOn: isOn))
                Circle()
                    .fill(isOn ? Color.white : Theme.green)
                    .frame(width: 22, height: 22)
                    .padding(4)
            }
        }
        .buttonStyle(.plain)
        .opacity(disabled ? 0.5 : 1)
        .disabled(disabled)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isOn)
    }

    private struct Track: ViewModifier {
        let isOn: Bool
        func body(content: Content) -> some View {
            if isOn { content.liquidGlass(Capsule(), fill: ActiveFill()) }
            else { content.liquidGlass(Capsule()) }
        }
    }
}

/// Чип инструмента с чекбоксом (фильтры, уведомления, отслеживаемые):
/// скругление 12, отступы 10, Min Sans 14 + иконка чекбокса 20, стекло.
struct InstrumentChip: View {
    let label: String
    @Binding var on: Bool
    /// Боковые отступы: в макете ширины чипов фиксированные, содержимое по
    /// центру — у инструментов это ~15 с каждой стороны (берём 14, чтобы
    /// ряды переносились как в макете при чуть более широком рендере шрифта), у статусов ~24.
    var hPadding: CGFloat = 14

    private let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)

    var body: some View {
        Button {
            Haptics.light()
            on.toggle()
        } label: {
            HStack(spacing: 4) {
                Text(label).font(Theme.sans(Theme.fontBase)).lineLimit(1)
                Icon(on ? AppIcons.checkboxOn : AppIcons.checkboxOff, size: 20, color: Theme.text)
            }
            .foregroundStyle(Theme.text)
            .padding(.horizontal, hPadding)
            .frame(height: 40)
            .contentShape(shape)
            .modifier(Fill(on: on, shape: shape))
        }
        .buttonStyle(.plain)
    }

    private struct Fill: ViewModifier {
        let on: Bool
        let shape: RoundedRectangle
        func body(content: Content) -> some View {
            if on { content.liquidGlass(shape, fill: ActiveFill()) }
            else { content.liquidGlass(shape) }
        }
    }
}

/// Сегменты («Неделя / Месяц / Год», «Все чаты / Личные чаты»): подложка
/// «Фон плашки 2» (или стекло), выбранный — пурпур «акцент 3», скругление 20.
struct SegmentedControl: View {
    @Binding var selection: String
    let options: [(value: String, label: String)]
    /// true — подложка стеклянная (как в «Сообществе»), иначе плашка #2D2E35.
    var glass: Bool = false
    /// false — выбранный пункт не подсвечивается (например, пока выбран диапазон дат).
    var highlight: Bool = true

    @Namespace private var lens

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.value) { opt in
                Button {
                    guard selection != opt.value else { return }
                    Haptics.light()
                    withAnimation(.spring(response: 0.38, dampingFraction: 0.8)) { selection = opt.value }
                } label: {
                    Text(opt.label)
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background {
                            if highlight && selection == opt.value {
                                SelectionLens(radius: Theme.radiusChip)
                                    .matchedGeometryEffect(id: "lens", in: lens)
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .frame(height: 56)
        .modifier(Container(glass: glass))
    }

    private struct Container: ViewModifier {
        let glass: Bool
        func body(content: Content) -> some View {
            let shape = RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous)
            if glass { content.liquidGlass(shape) }
            else { content.background(Theme.surface2Plate, in: shape) }
        }
    }
}

/// Пурпурная «линза» выбранного пункта (чипы материалов, сегменты): стекло с
/// заливкой «акцент 3».
struct SelectionLens: View {
    var radius: CGFloat = Theme.radiusChip
    var body: some View {
        // Без системного glassEffect: он рисуется поверх обрезки предков и вылезал
        // за край ленты при прокрутке. Заливка и кромка стекла строго в форме.
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        Color.clear
            .background(PurpleFill().clipShape(shape))
            .overlay(GlassRim(shape: shape).allowsHitTesting(false))
    }
}

/// Button/в отчете и Button/Button chart: 48 pt, скругление 24, текст 14 слева,
/// справа кружок 32 с иконкой 20.
struct ReportActionButton: View {
    let label: String
    /// accent — «Открыть отчет»; pink — «Обсудить»; chartOff — «Открыть график»
    /// (белая обводка, белый кружок); chartOn — «Закрыть график» (стекло).
    enum Variant { case accent, pink, chartOff, chartOn }
    var variant: Variant = .accent
    let icon: IconDef
    let action: () -> Void

    private let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)

    var body: some View {
        Button(action: action) {
            HStack {
                Text(label).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                Spacer(minLength: 8)
                Icon(icon, size: 20, color: variant == .chartOff ? Theme.green : .white)
                    .frame(width: 32, height: 32)
                    .background(badge, in: Circle())
            }
            .padding(.leading, 16)
            .padding(.trailing, 8)
            .frame(height: 48)
            .contentShape(shape)
            .modifier(Fill(variant: variant, shape: shape))
        }
        .buttonStyle(PressableStyle())
    }

    private var badge: Color {
        switch variant {
        case .pink: return Color(hex: "#da517d")
        case .chartOff: return .white
        default: return Theme.green
        }
    }

    private struct Fill: ViewModifier {
        let variant: Variant
        let shape: RoundedRectangle
        func body(content: Content) -> some View {
            switch variant {
            case .accent: content.liquidGlass(shape, fill: ActiveFill())
            case .pink: content.liquidGlass(shape, fill: PinkFill())
            case .chartOn: content.liquidGlass(shape)
            case .chartOff: content.overlay(shape.stroke(Color.white, lineWidth: 1))
            }
        }
    }
}

/// Маленькая пилюля-действие «Искать» (Button/искать): 42 pt, скругление 44,
/// Min Sans 13 с трекингом 0.52, стекло с «active».
struct PillActionButton: View {
    let title: String
    var height: CGFloat = 42
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.sans(13))
                .tracking(0.52)
                .foregroundStyle(Theme.text)
                .padding(.horizontal, 12)
                .frame(height: height)
                .liquidGlass(Capsule(), fill: ActiveFill())
        }
        .buttonStyle(PressableStyle())
    }
}

/// Пурпурная пилюля «Отвязать / Привязать / Добавить / Удалить» (32 pt).
struct PurplePillButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.sans(13))
                .tracking(0.52)
                .foregroundStyle(Theme.text)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .liquidGlass(Capsule(), fill: PurpleFill())
                .clipShape(Capsule())
        }
        .buttonStyle(PressableStyle())
    }
}
