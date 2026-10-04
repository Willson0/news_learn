import UIKit

/// Лёгкая тактильная отдача (аналог haptic() из telegram/webapp.js).
enum Haptics {
    static func light() { impact(.light) }
    static func medium() { impact(.medium) }
    static func heavy() { impact(.heavy) }
    static func rigid() { impact(.rigid) }

    private static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        let g = UIImpactFeedbackGenerator(style: style)
        g.prepare()
        g.impactOccurred()
    }
}
