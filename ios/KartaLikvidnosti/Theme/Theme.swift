import SwiftUI

/// Дизайн-токены «Карта Ликвидности» — перенос из frontend/src/styles/tokens.css.
/// Тёмная тема, мобильный макет 393×856.
enum Theme {

    // MARK: - Цвета поверхностей
    static let bg = Color(hex: "#1a1b1d")
    static let surface = Color(hex: "#25262a")
    static let surface2 = Color(hex: "#313131")
    static let surfaceMuted = Color(hex: "#313131").opacity(0.2)
    static let navbarBg = Color(hex: "#161616").opacity(0.2)
    static let badge = Color(hex: "#555555")

    // MARK: - Текст
    static let text = Color.white
    static let textSecondary = Color.white.opacity(0.65)
    static let textMuted = Color.white.opacity(0.4)

    // MARK: - Акценты
    static let danger = Color(hex: "#9c1f1f")
    static let error = Color(hex: "#ff4d4d")
    static let green = Color(hex: "#5bb318")
    static let greenSend = Color(hex: "#4e8f10")

    /// Основной оливково-зелёный градиент (кнопки, активная вкладка).
    static let gradientAccent = LinearGradient(
        stops: [
            .init(color: Color(hex: "#c2ab1f"), location: 0.0),
            .init(color: Color(hex: "#8f9a26"), location: 0.24),
            .init(color: Color(hex: "#3f6a16"), location: 0.64),
            .init(color: Color(hex: "#1b4a0b"), location: 1.0),
        ],
        startPoint: .leading, endPoint: .trailing
    )

    /// Приглушённый вариант акцента (мягкие плашки/кнопки на карточке).
    static let gradientAccentSoft = LinearGradient(
        stops: [
            .init(color: Color(hex: "#ffd900").opacity(0.2), location: 0.0),
            .init(color: Color(hex: "#1c4a00").opacity(0.2), location: 1.0),
        ],
        startPoint: .leading, endPoint: .trailing
    )

    /// Пурпурный акцент (активный чип «Все», вкладки).
    static let gradientAccentPurple = LinearGradient(
        stops: [
            .init(color: Color(hex: "#572546").opacity(0.6), location: 0.0),
            .init(color: Color(hex: "#bd5197").opacity(0.6), location: 1.0),
        ],
        startPoint: .leading, endPoint: .trailing
    )

    /// Насыщенный пурпур для кнопок-пилюль (Отвязать/Привязать).
    static let gradientPurpleSolid = LinearGradient(
        colors: [Color(hex: "#57253f"), Color(hex: "#bd5197")],
        startPoint: .leading, endPoint: .trailing
    )

    // MARK: - Типографика
    static let fontTitle: CGFloat = 24
    static let fontLg: CGFloat = 17
    static let fontMd: CGFloat = 16
    static let fontBase: CGFloat = 14
    static let fontSm: CGFloat = 12

    static func heading(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight)
    }

    // MARK: - Радиусы
    static let radiusCard: CGFloat = 24
    static let radiusChip: CGFloat = 20
    static let radiusPill: CGFloat = 60
    static let radiusInput: CGFloat = 24

    // MARK: - Отступы и размеры
    static let screenX: CGFloat = 16
    static let appWidth: CGFloat = 393
    static let appHeight: CGFloat = 856
}

// MARK: - Удобные модификаторы

extension View {
    func screenBackground(_ background: some ShapeStyle = Theme.bg) -> some View {
        self.frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(background.ignoresSafeArea())
    }

    func screenBackground<B: View>(@ViewBuilder _ background: () -> B) -> some View {
        self.frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(background().ignoresSafeArea())
    }

    func cardSurface(radius: CGFloat = Theme.radiusCard) -> some View {
        self.background(Theme.surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}
