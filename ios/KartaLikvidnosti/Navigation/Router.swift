import SwiftUI

enum Tab: String, CaseIterable {
    case home, analytics, community, profile
}

/// Экраны, открываемые поверх корневой вкладки (аналог pushed-маршрутов роутера).
enum Route: Hashable {
    case reportDetail(id: Int?)
    case reportCreate
    case reportEdit(id: Int)
    case chat(id: String)
    case chatInfo(id: String)
    case chatEdit(id: String)
    case chatUser(id: String, uid: String)
    case profileEdit
    case notifications
    case accountData
    case accountChange(field: String)
    case accountSuccess(field: String)
    case admins
    case subscription
}

/// Маршруты экрана авторизации (hideNav).
enum AuthRoute: Hashable {
    case registerConfirm(email: String)
    case passwordRecovery
}

@MainActor
final class Router: ObservableObject {
    @Published var authed: Bool
    @Published var tab: Tab = .home
    @Published var path: [Route] = []
    @Published var authPath: [AuthRoute] = []

    init() { authed = TokenStore.isAuthenticated }

    func push(_ route: Route) { path.append(route) }
    func pop() { if !path.isEmpty { path.removeLast() } }
    func popToRoot() { path.removeAll() }

    func switchTab(_ t: Tab) {
        tab = t
        path.removeAll()
    }

    func enterApp() {
        authed = true
        path.removeAll()
        authPath.removeAll()
    }

    func logout() {
        authed = false
        path.removeAll()
        authPath.removeAll()
        tab = .home
    }
}
