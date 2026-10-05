import SwiftUI

// Базовые элементы управления (перенос ui/*.vue).

enum AppButtonVariant { case accent, soft, secondary, danger }

struct AppButton: View {
    let title: String
    var variant: AppButtonVariant = .accent
    var disabled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: { if !disabled { action() } }) {
            Text(title)
                .font(.system(size: Theme.fontBase))
                .foregroundStyle(Theme.text)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(background)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
        }
        .buttonStyle(PressableStyle())
        .opacity(disabled ? 0.5 : 1)
        .disabled(disabled)
    }

    @ViewBuilder private var background: some View {
        switch variant {
        case .accent: Theme.gradientAccent
        case .soft: Theme.gradientAccentSoft
        case .secondary: Theme.surfaceMuted
        case .danger: Theme.danger
        }
    }
}

struct AppInput: View {
    @Binding var text: String
    var placeholder: String = ""
    var isSecure: Bool = false
    var error: Bool = false
    var keyboard: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil

    @State private var revealed = false

    var body: some View {
        HStack(spacing: 8) {
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
            .foregroundStyle(error ? Theme.error : Theme.text)
            .textContentType(textContentType)
            .tint(Theme.green)

            if isSecure {
                Button {
                    revealed.toggle()
                } label: {
                    Icon(revealed ? AppIcons.eye : AppIcons.eyeClosed, size: 24, color: Theme.text)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 48)
        .background(Theme.surfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
    }

    private var prompt: Text {
        Text(placeholder).foregroundColor(error ? Theme.error : Theme.textMuted)
    }
}

struct AppToggle: View {
    @Binding var isOn: Bool
    var disabled: Bool = false

    var body: some View {
        Button {
            if !disabled { Haptics.light(); isOn.toggle() }
        } label: {
            ZStack(alignment: isOn ? .trailing : .leading) {
                Capsule()
                    .fill(AnyShapeStyle(isOn ? AnyShapeStyle(Theme.gradientAccent) : AnyShapeStyle(Theme.surface2)))
                    .frame(width: 56, height: 30)
                Circle()
                    .fill(isOn ? Color.white : Theme.green)
                    .frame(width: 24, height: 24)
                    .padding(3)
            }
        }
        .buttonStyle(.plain)
        .opacity(disabled ? 0.5 : 1)
        .disabled(disabled)
        .animation(.easeInOut(duration: 0.18), value: isOn)
    }
}

struct InstrumentChip: View {
    let label: String
    @Binding var on: Bool

    var body: some View {
        Button {
            on.toggle()
        } label: {
            HStack(spacing: 8) {
                Text(label).font(.system(size: Theme.fontBase))
                ZStack {
                    RoundedRectangle(cornerRadius: 5)
                        .stroke(on ? Color.white.opacity(0.9) : Color.white.opacity(0.6), lineWidth: 1.5)
                        .background(on ? Color.black.opacity(0.25) : Color.clear)
                        .frame(width: 18, height: 18)
                    if on {
                        Image(systemName: "checkmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
            }
            .foregroundStyle(Theme.text)
            .padding(.horizontal, 12)
            .frame(height: 40)
            .background(chipBackground)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusChip, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radiusChip, style: .continuous)
                    .stroke(on ? Color.clear : Color.white.opacity(0.12), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var chipBackground: some View {
        if on { Theme.gradientAccent } else { Color.clear }
    }
}

struct SegmentedControl: View {
    @Binding var selection: String
    let options: [(value: String, label: String)]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.value) { opt in
                Button {
                    selection = opt.value
                } label: {
                    Text(opt.label)
                        .font(.system(size: Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(selection == opt.value ? AnyShapeStyle(Theme.gradientAccentPurple) : AnyShapeStyle(Color.clear),
                                    in: RoundedRectangle(cornerRadius: Theme.radiusChip, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(Theme.surfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}

struct ReportActionButton: View {
    let label: String
    enum Variant { case accent, purple, dark }
    enum Badge { case green, pink }
    var variant: Variant = .accent
    var badge: Badge = .green
    let icon: IconDef
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(label).font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
                Spacer(minLength: 8)
                ZStack {
                    Circle().fill(badge == .green ? Color(hex: "#4e9c17") : Color(hex: "#d7609f"))
                        .frame(width: 40, height: 40)
                    Icon(icon, size: 22, color: .white)
                }
            }
            .padding(.leading, 20)
            .padding(.trailing, 8)
            .frame(height: 56)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusPill, style: .continuous))
        }
        .buttonStyle(PressableStyle())
    }

    @ViewBuilder private var background: some View {
        switch variant {
        case .accent: Theme.gradientAccent
        case .purple: LinearGradient(colors: [Color(hex: "#3a1230"), Color(hex: "#9c2f6e")], startPoint: .leading, endPoint: .trailing)
        case .dark: Theme.surfaceMuted
        }
    }
}
