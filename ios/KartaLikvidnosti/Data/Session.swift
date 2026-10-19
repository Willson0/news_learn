import SwiftUI

/// Текущий пользователь и его права (аналог frontend/src/api/session.js).
@MainActor
final class Session: ObservableObject {
    @Published var user: UserDto?
    @Published var loaded: Bool = false
    /// Активна ли подписка (nil — ещё не знаем).
    @Published var subscriptionActive: Bool?

    /// «Доступный экран профиля» из макета: без активной подписки (и не
    /// админ) доступны только общий чат и профиль с оплатой.
    var restricted: Bool { user != nil && !isAdmin && subscriptionActive == false }

    var isAdmin: Bool { user?.isAdmin == true }
    var isAuthenticated: Bool { TokenStore.isAuthenticated }

    func setCurrent(_ u: UserDto?) {
        user = u
        loaded = true
        Task { await loadSubscription() }
    }

    func clear() {
        user = nil
        subscriptionActive = nil
    }

    func loadSubscription() async {
        guard TokenStore.isAuthenticated else { subscriptionActive = nil; return }
        // Нет записи подписки (сервер отдаёт data: null) — это неактивная подписка.
        do {
            subscriptionActive = try await Api.fetchSubscription()?.active ?? false
        } catch {}
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
