import SwiftUI
import UIKit

/// Анимации из прототипа Figma (файл MtvMQiOEz1qNOWzAl2deus, страница «Дизайн»).
/// Значения взяты из реакций прототипа: тип перехода, кривая и длительность.
enum Motion {
    /// Системное «Уменьшение движения»: вместо пружин и back-кривых — короткое
    /// плавное проявление (fade) без перелётов.
    static var reduced: Bool { UIAccessibility.isReduceMotionEnabled }
    private static let fade = Animation.easeInOut(duration: 0.2)

    /// Figma EASE_IN_AND_OUT_BACK — cubic-bezier(0.7, -0.4, 0.4, 1.4).
    static func back(_ duration: Double = 0.8) -> Animation {
        reduced ? fade : .timingCurve(0.7, -0.4, 0.4, 1.4, duration: duration)
    }

    /// SMART_ANIMATE / DISSOLVE, EASE_IN_AND_OUT_BACK, 800 мс — навбар, попапы,
    /// чипсы, свитчи, вкладки «Инфо о чате», сообщения в чате.
    static var standard: Animation { back() }

    /// Figma SLOW (пружина: масса 1, жёсткость 80, затухание 20; ≈1.25 с) —
    /// раскрытие графика в отчёте, лист фильтров аналитики.
    static var slow: Animation { reduced ? fade : .interpolatingSpring(mass: 1, stiffness: 80, damping: 20) }

    /// Figma EASE_OUT 300 мс — поля ввода на экранах авторизации.
    static let authField = Animation.timingCurve(0, 0, 0.58, 1, duration: 0.3)

    /// Figma DISSOLVE 100 мс — выбор даты в календаре.
    static let calendar = Animation.easeInOut(duration: 0.1)

    /// Figma EASE_IN_AND_OUT (0.42, 0, 0.58, 1).
    static func inOut(_ duration: Double) -> Animation {
        .timingCurve(0.42, 0, 0.58, 1, duration: duration)
    }
}
