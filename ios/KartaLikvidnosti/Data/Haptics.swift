import UIKit

/// Лёгкая тактильная отдача (аналог haptic() из telegram/webapp.js).
enum Haptics {
    static func light() { impact(.light) }
    static func medium() { impact(.medium) }
    static func heavy() { impact(.heavy) }
    static func rigid() { impact(.rigid) }
    static func soft() { impact(.soft) }
    /// Короткий «щелчок» выбора — для порогов жеста (например, почти замок).
    static func tick() {
        let g = UISelectionFeedbackGenerator()
        g.prepare()
        g.selectionChanged()
    }
    static func success() { notify(.success) }
    static func warning() { notify(.warning) }

    private static func notify(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        let g = UINotificationFeedbackGenerator()
        g.prepare()
        g.notificationOccurred(type)
    }

    private static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        let g = UIImpactFeedbackGenerator(style: style)
        g.prepare()
        g.impactOccurred()
    }
}
