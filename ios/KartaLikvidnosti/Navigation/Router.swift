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
    case userAgreement
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
    /// У каждой вкладки системного TabView свой стек pushed-экранов.
    @Published var paths: [Tab: [Route]] = [:]
    @Published var authPath: [AuthRoute] = []

    init() { authed = TokenStore.isAuthenticated }

    /// Стек текущей вкладки.
    var path: [Route] {
        get { paths[tab] ?? [] }
        set { paths[tab] = newValue }
    }

    func path(for t: Tab) -> Binding<[Route]> {
        Binding(get: { self.paths[t] ?? [] }, set: { self.paths[t] = $0 })
    }

    func push(_ route: Route) { path.append(route) }
    func pop() { if !path.isEmpty { path.removeLast() } }
    func popToRoot() { path.removeAll() }

    /// Программный переход на вкладку (из pushed-экрана): закрываем текущий стек
    /// и открываем вкладку с корня.
    func switchTab(_ t: Tab) {
        paths[tab] = []
        paths[t] = []
        tab = t
    }

    func enterApp() {
        authed = true
        paths = [:]
        authPath.removeAll()
    }

    func logout() {
        authed = false
        paths = [:]
        authPath.removeAll()
        tab = .home
    }
}
