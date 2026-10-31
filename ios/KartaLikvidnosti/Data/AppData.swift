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
    /// Когда список чатов последний раз пришёл с сервера.
    @Published var chatsAt: Date? = nil
    @Published var profile: ProfileResponse? = nil
    /// Последнее известное состояние подписки: экраны «Профиль» и «Управление подпиской»
    /// открываются сразу с ним, без анимации из пустого состояния.
    @Published var subscription: SubscriptionDto? = nil
    /// Привязанная карта — тоже для мгновенного показа «Управления подпиской».
    @Published var card: CardDto? = nil
    @Published var preloaded = false

    init() { seedFromDisk() }

    /// Холодный старт: последние данные с диска (лента, чаты, профиль, подписка) —
    /// экраны открываются сразу с ними, даже без сети, а свежие приходят в фоне.
    func seedFromDisk() {
        guard TokenStore.isAuthenticated else { return }
        if let r = ApiClient.cached("reports", as: DataWrapper<[ReportDto]>.self) {
            reports = r.value.data ?? []; reportsAt = r.savedAt
        }
        if let c = ApiClient.cached("chats", as: DataWrapper<[ChatSectionDto]>.self), let list = c.value.data, !list.isEmpty {
            chats = list; chatsAt = c.savedAt
        }
        if let p = ApiClient.cached("profile", as: ProfileResponse.self) {
            profile = p.value; subscription = p.value.subscription
        }
        if let s = ApiClient.cached("subscription", as: DataWrapper<SubscriptionDto>.self), let sub = s.value.data {
            subscription = sub
        }
    }

    /// Тихо подгружает ленту, чаты и профиль. Ошибки гасим — экраны обновят сами.
    func preload() async {
        guard TokenStore.isAuthenticated else {
            reports = []; reportsAt = nil; chats = []; chatsAt = nil; profile = nil; subscription = nil; card = nil; preloaded = false
            return
        }
        // Лента и чаты — параллельно: на медленной сети вторая вкладка не ждёт первую.
        async let r: [ReportDto]? = try? await Api.fetchReports()
        async let c: [ChatSectionDto]? = try? await Api.fetchChats()
        if let r = await r { reports = r; reportsAt = Date() }
        if let c = await c, !c.isEmpty { chats = c; chatsAt = Date() }
        if let p = try? await Api.fetchProfile() { profile = p; subscription = p.subscription ?? subscription }
        preloaded = true
    }

    func clear() {
        OfflineCache.clear()
        reports = []; reportsAt = nil; chats = []; chatsAt = nil; profile = nil; subscription = nil; card = nil; preloaded = false
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
