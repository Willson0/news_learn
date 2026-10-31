import Foundation

/// Даты чата. Сервер отдаёт полное время в ISO 8601 (`created_at`, `last_at`),
/// приложение само переводит его в часовой пояс телефона и подписывает:
/// «Сегодня», «Вчера», «12 октября».
enum ChatDates {
    private static let ru = Locale(identifier: "ru_RU")

    private static let isoPlain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
    private static let isoFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static func formatter(_ format: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = ru
        f.dateFormat = format
        return f
    }
    private static let timeFmt = formatter("HH:mm")
    private static let dayFmt = formatter("d MMMM")
    private static let dayYearFmt = formatter("d MMMM yyyy")
    private static let shortDayFmt = formatter("d MMM")
    private static let shortFmt = formatter("dd.MM.yy")

    /// Сейчас в ISO 8601 — время отправки у своего сообщения до ответа сервера.
    static func isoNow() -> String { isoPlain.string(from: Date()) }

    static func parse(_ iso: String?) -> Date? {
        guard let iso, !iso.isEmpty else { return nil }
        return isoPlain.date(from: iso) ?? isoFraction.date(from: iso)
    }

    /// Начало дня сообщения; у ещё не отправленного (без даты) — сегодня.
    static func day(_ iso: String?) -> Date {
        Calendar.current.startOfDay(for: parse(iso) ?? Date())
    }

    /// «16:32» в часовом поясе телефона; без даты — то, что прислал сервер.
    static func time(_ iso: String?, fallback: String? = nil) -> String? {
        guard let d = parse(iso) else { return fallback }
        return timeFmt.string(from: d)
    }

    /// Разделитель в ленте: «Сегодня», «Вчера», «12 октября», «12 октября 2025».
    static func dayLabel(_ day: Date) -> String {
        let cal = Calendar.current
        if cal.isDateInToday(day) { return "Сегодня" }
        if cal.isDateInYesterday(day) { return "Вчера" }
        if cal.component(.year, from: day) == cal.component(.year, from: Date()) { return dayFmt.string(from: day) }
        return dayYearFmt.string(from: day)
    }

    /// Список чатов: время для сегодня, «вчера», дальше дата.
    static func listStamp(_ iso: String?, fallback: String? = nil) -> String {
        guard let d = parse(iso) else { return fallback ?? "" }
        let cal = Calendar.current
        if cal.isDateInToday(d) { return timeFmt.string(from: d) }
        if cal.isDateInYesterday(d) { return "вчера" }
        if cal.component(.year, from: d) == cal.component(.year, from: Date()) {
            return shortDayFmt.string(from: d).replacingOccurrences(of: ".", with: "")
        }
        return shortFmt.string(from: d)
    }

    /// Подпись в просмотре фото: «Сегодня в 16:32», «12 октября в 09:05».
    static func stamp(_ iso: String?, fallback: String? = nil) -> String {
        guard let d = parse(iso) else { return fallback ?? "" }
        return "\(dayLabel(d)) в \(timeFmt.string(from: d))"
    }
}
