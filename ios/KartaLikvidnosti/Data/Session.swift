import SwiftUI

/// Текущий пользователь и его права (аналог frontend/src/api/session.js).
@MainActor
final class Session: ObservableObject {
    @Published var user: UserDto? { didSet { rememberRestricted() } }
    @Published var loaded: Bool = false
    /// Активна ли подписка (nil — ещё не знаем). Правило — `SubscriptionDto.active`.
    @Published var subscriptionActive: Bool? { didSet { rememberRestricted() } }
    /// Дата окончания последнего известного статуса: без сети истёкшая подписка всё равно закрывается.
    private var subscriptionUntil: String?

    /// Режим без подписки с прошлого запуска: пока пользователь и подписка
    /// грузятся, экраны сразу открываются в нём, без мелькания полного режима.
    private var lastRestricted = UserDefaults.standard.bool(forKey: Session.restrictedKey)
    private static let restrictedKey = "kl_restricted"

    /// «Доступный экран профиля» из макета: без активной подписки (и не
    /// админ) доступны только общий чат и профиль с оплатой.
    var restricted: Bool {
        guard TokenStore.isAuthenticated else { return false }
        if let user, let active = subscriptionActive { return !user.isAdmin && !active }
        if user?.isAdmin == true { return false }
        return lastRestricted
    }

    private func rememberRestricted() {
        guard let user, let active = subscriptionActive else { return }
        lastRestricted = !user.isAdmin && !active
        UserDefaults.standard.set(lastRestricted, forKey: Self.restrictedKey)
    }

    init() {
        // Холодный старт без сети: пользователь и подписка с прошлого раза — права
        // (админ, режим без подписки) известны с первого кадра, разлогина нет.
        guard TokenStore.isAuthenticated else { return }
        user = ApiClient.cached("user", as: MeResponse.self)?.value.user
            ?? ApiClient.cached("profile", as: ProfileResponse.self)?.value.user
        if let sub = ApiClient.cached("subscription", as: DataWrapper<SubscriptionDto>.self)?.value.data {
            subscriptionUntil = sub.until
            subscriptionActive = sub.active
        }
    }

    var isAdmin: Bool { user?.isAdmin == true }
    var isAuthenticated: Bool { TokenStore.isAuthenticated }

    /// После входа: подписку узнаём до показа приложения, чтобы без неё
    /// сразу открылся профиль, а не мелькнула главная.
    func setCurrent(_ u: UserDto?) async {
        lastRestricted = false
        subscriptionActive = nil
        subscriptionUntil = nil
        user = u
        loaded = true
        if u != nil { Push.activate() }
        await loadSubscription()
    }

    func clear() {
        user = nil
        subscriptionActive = nil
        subscriptionUntil = nil
        lastRestricted = false
        UserDefaults.standard.removeObject(forKey: Self.restrictedKey)
    }

    func loadSubscription() async {
        guard TokenStore.isAuthenticated else { subscriptionActive = nil; return }
        // Запуск прошёл без сети — пользователя дочитываем при первой возможности.
        if user == nil, let me = try? await Api.fetchMe().user { user = me }
        // Нет записи подписки (сервер отдаёт data: null) — это неактивная подписка.
        do {
            setSubscription(try await Api.fetchSubscription())
        } catch {
            if subscriptionActive == true && !SubscriptionDto.untilNotPassed(subscriptionUntil) {
                subscriptionActive = false
            }
        }
    }

    /// Свежий статус подписки (из профиля, экрана подписки или обновления).
    /// После оплаты приложение разблокируется на месте: навбар и экраны
    /// переходят в полный режим с анимацией прототипа.
    func setSubscription(_ sub: SubscriptionDto?) {
        subscriptionUntil = sub?.until
        let active = sub?.active ?? false
        guard subscriptionActive != active else { return }
        let wasRestricted = restricted
        if wasRestricted && active {
            withAnimation(Motion.standard) { subscriptionActive = active }
        } else {
            subscriptionActive = active
        }
    }

    /// Подгружает пользователя, если есть токен. Тихо гасит ошибки.
    func load(force: Bool = false) async {
        if !TokenStore.isAuthenticated {
            user = nil
            loaded = true
            return
        }
        if loaded && !force && user != nil { return }
        do {
            user = try await Api.fetchMe().user
        } catch {
            // оставляем прежнее значение
        }
        await loadSubscription()
        loaded = true
    }
}
