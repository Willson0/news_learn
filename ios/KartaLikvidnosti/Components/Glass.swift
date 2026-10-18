import SwiftUI

// Стеклянные элементы и заливки из UI-кита макета («дизайн система / uikit»).

/// Линейный градиент в семантике CSS `linear-gradient(<angle>, …)`, как его
/// отдаёт Figma: угол от «вверх» по часовой, линия градиента проходит через
/// центр и имеет длину |w·sinθ| + |h·cosθ|.
///
/// Figma задаёт градиент в координатах самого слоя, поэтому один и тот же стиль
/// на элементах разных пропорций даёт разный CSS-угол. `squareAngle` — угол на
/// квадратном элементе; для w×h он пересчитывается как atan(tan(a)·h/w).
struct CSSLinearGradient: View {
    struct Stop { let color: Color; let location: CGFloat }
    var squareAngle: Double
    var stops: [Stop]

    var body: some View {
        GeometryReader { geo in
            let w = max(geo.size.width, 1), h = max(geo.size.height, 1)
            let theta = Self.angle(squareAngle: squareAngle, w: w, h: h)
            let dx = sin(theta), dy = -cos(theta)
            let len = abs(w * dx) + abs(h * dy)
            let start = UnitPoint(x: 0.5 - dx * len / 2 / w, y: 0.5 - dy * len / 2 / h)
            let end = UnitPoint(x: 0.5 + dx * len / 2 / w, y: 0.5 + dy * len / 2 / h)
            LinearGradient(
                stops: stops.map { Gradient.Stop(color: $0.color, location: $0.location) },
                startPoint: start, endPoint: end
            )
        }
    }

    static func angle(squareAngle: Double, w: CGFloat, h: CGFloat) -> CGFloat {
        let a = squareAngle * .pi / 180
        // Сохраняем квадрант: считаем по компонентам направления.
        let sx = sin(a), cy = cos(a)
        return CGFloat(atan2(sx * Double(h), cy * Double(w)))
    }
}

/// Заливка стиля «active»: два слоя по 20% —
/// 1) 90°: #FFD900 → #1C4A00; 2) диагональ #FFFF00 (4%) → #1BEB00 (70%).
struct ActiveFill: View {
    var body: some View {
        ZStack {
            CSSLinearGradient(squareAngle: 38.6133, stops: [
                .init(color: Color(hex: "#ffff00").opacity(0.2), location: 0.042691),
                .init(color: Color(hex: "#1beb00").opacity(0.2), location: 0.70449),
            ])
            LinearGradient(
                colors: [Color(hex: "#ffd900").opacity(0.2), Color(hex: "#1c4a00").opacity(0.2)],
                startPoint: .leading, endPoint: .trailing
            )
        }
    }
}

/// Заливка стиля «акцент 3»: #572546 (30%) → #BD5197 (111%), обе 60%.
struct PurpleFill: View {
    var body: some View {
        CSSLinearGradient(squareAngle: 83.9115, stops: [
            .init(color: Color(hex: "#572546").opacity(0.6), location: 0.30441),
            .init(color: Color(hex: "#bd5197").opacity(0.6), location: 1.1103),
        ])
    }
}

/// Розово-малиновая заливка кнопки «Обсудить» (#DA517D → #FF0052, 40%).
struct PinkFill: View {
    var body: some View {
        CSSLinearGradient(squareAngle: 269.0, stops: [
            .init(color: Color(hex: "#da517d").opacity(0.4), location: 0.012609),
            .init(color: Color(hex: "#ff0052").opacity(0.4), location: 0.96049),
        ])
    }
}

/// Круглая стеклянная кнопка 42×42 (Button/back, Button/Settings, Button/add …).
struct GlassCircleButton: View {
    let icon: IconDef
    var iconSize: CGFloat = 24
    var size: CGFloat = 42
    var iconColor: Color = Theme.text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Icon(icon, size: iconSize, color: iconColor)
                .frame(width: size, height: size)
                .contentShape(Circle())
                .liquidGlass(Circle(), interactive: true)
        }
        .buttonStyle(PressableStyle())
    }
}

/// Кнопка «назад» из макета: стеклянный круг 42 с шевроном 22.
struct GlassBackButton: View {
    var action: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        GlassCircleButton(icon: AppIcons.back, iconSize: 22) {
            Haptics.light()
            if let action { action() } else { dismiss() }
        }
    }
}

/// Стеклянная плашка-лейбл (Label/label): «Актуальный», «Сегодня», «Готово».
struct GlassLabel: View {
    let text: String
    var height: CGFloat? = nil
    var font: Font = Theme.sans(Theme.fontSm, .semibold)

    var body: some View {
        Text(text)
            .font(font)
            .foregroundStyle(Theme.text)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .frame(height: height)
            .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

/// Верхняя панель внутреннего экрана: «назад» слева на y=78 (24 от статус-бара
/// макета), опционально — элемент по центру и справа. Ставится в `.overlay(alignment: .top)`
/// экрана, который игнорирует верхнюю safe area (координаты как в макете).
struct GlassTopBar<Center: View, Trailing: View>: View {
    var onBack: (() -> Void)? = nil
    @ViewBuilder var center: () -> Center
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        ZStack {
            center()
            HStack {
                GlassBackButton(action: onBack)
                Spacer()
                trailing()
            }
        }
        .frame(height: 42)
        .padding(.horizontal, Theme.screenX)
        .padding(.top, Theme.topBarY)
    }
}

extension GlassTopBar where Center == EmptyView, Trailing == EmptyView {
    init(onBack: (() -> Void)? = nil) {
        self.onBack = onBack
        self.center = { EmptyView() }
        self.trailing = { EmptyView() }
    }
}

extension GlassTopBar where Center == EmptyView {
    init(onBack: (() -> Void)? = nil, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.onBack = onBack
        self.center = { EmptyView() }
        self.trailing = trailing
    }
}
