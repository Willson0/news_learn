import SwiftUI

/// Текущий пользователь и его права (аналог frontend/src/api/session.js).
@MainActor
final class Session: ObservableObject {
    @Published var user: UserDto?
    @Published var loaded: Bool = false

    var isAdmin: Bool { user?.isAdmin == true }
    var isAuthenticated: Bool { TokenStore.isAuthenticated }

    func setCurrent(_ u: UserDto?) {
        user = u
        loaded = true
    }

    func clear() {
        user = nil
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
        loaded = true
    }
}
