import SwiftUI

/// Текущий пользователь и его права (аналог frontend/src/api/session.js).
@MainActor
final class Session: ObservableObject {
    @Published var user: UserDto? { didSet { rememberRestricted() } }
    @Published var loaded: Bool = false
    /// Активна ли подписка (nil — ещё не знаем).
    @Published var subscriptionActive: Bool? { didSet { rememberRestricted() } }

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

    var isAdmin: Bool { user?.isAdmin == true }
    var isAuthenticated: Bool { TokenStore.isAuthenticated }

    /// После входа: подписку узнаём до показа приложения, чтобы без неё
    /// сразу открылся профиль, а не мелькнула главная.
    func setCurrent(_ u: UserDto?) async {
        lastRestricted = false
        subscriptionActive = nil
        user = u
        loaded = true
        if u != nil { Push.activate() }
        await loadSubscription()
    }

    func clear() {
        user = nil
        subscriptionActive = nil
        lastRestricted = false
        UserDefaults.standard.removeObject(forKey: Self.restrictedKey)
    }

    func loadSubscription() async {
        guard TokenStore.isAuthenticated else { subscriptionActive = nil; return }
        // Нет записи подписки (сервер отдаёт data: null) — это неактивная подписка.
        do {
            setSubscription(try await Api.fetchSubscription()?.active ?? false)
        } catch {}
    }

    /// Свежий статус подписки (из профиля, экрана подписки или обновления).
    /// После оплаты приложение разблокируется на месте: навбар и экраны
    /// переходят в полный режим с анимацией прототипа.
    func setSubscription(_ active: Bool) {
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
