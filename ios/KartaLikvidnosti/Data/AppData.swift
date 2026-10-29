import SwiftUI

/// Общий кэш основных данных приложения. Предзагружается на старте (при наличии
/// токена) и при входе, чтобы при базовой навигации между вкладками не было
/// прогрузок: экраны берут данные отсюда мгновенно, а затем обновляют их.
@MainActor
final class AppData: ObservableObject {
    @Published var reports: [ReportDto] = []
    /// Когда лента последний раз пришла с сервера (nil — ещё не загружалась) — для плашки «Показаны данные от 14:05».
    @Published var reportsAt: Date? = nil
    @Published var chats: [ChatSectionDto] = []
    @Published var profile: ProfileResponse? = nil
    /// Последнее известное состояние подписки: экраны «Профиль» и «Управление подпиской»
    /// открываются сразу с ним, без анимации из пустого состояния.
    @Published var subscription: SubscriptionDto? = nil
    /// Привязанная карта — тоже для мгновенного показа «Управления подпиской».
    @Published var card: CardDto? = nil
    @Published var preloaded = false

    /// Тихо подгружает ленту, чаты и профиль. Ошибки гасим — экраны обновят сами.
    func preload() async {
        guard TokenStore.isAuthenticated else {
            reports = []; reportsAt = nil; chats = []; profile = nil; subscription = nil; card = nil; preloaded = false
            return
        }
        if let r = try? await Api.fetchReports() { reports = r; reportsAt = Date() }
        if let c = try? await Api.fetchChats(), !c.isEmpty { chats = c }
        if let p = try? await Api.fetchProfile() { profile = p; subscription = p.subscription ?? subscription }
        preloaded = true
    }

    func clear() {
        reports = []; reportsAt = nil; chats = []; profile = nil; subscription = nil; card = nil; preloaded = false
    }
}

/// Применить данные без анимаций: значения с сервера встают на место сразу,
/// а не «доезжают» из прежнего состояния (переключатели, чипы, статусы).
@MainActor
func withoutAnimation(_ body: () -> Void) {
    var t = Transaction()
    t.disablesAnimations = true
    withTransaction(t, body)
}
