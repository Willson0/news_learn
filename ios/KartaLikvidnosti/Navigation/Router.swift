import SwiftUI

enum Tab: String, CaseIterable {
    case home, analytics, community, profile

    /// Название вкладки для VoiceOver.
    var label: String {
        switch self {
        case .home: return "Главная"
        case .analytics: return "Аналитика"
        case .community: return "Сообщество"
        case .profile: return "Профиль"
        }
    }
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
    case registerProfile
}

@MainActor
final class Router: ObservableObject {
    @Published var authed: Bool
    @Published var tab: Tab = .home {
        didSet {
            guard oldValue != tab else { return }
            previousTab = oldValue
            tabEntry += 1
            entryAtRoot = (paths[tab] ?? []).isEmpty
        }
    }
    /// Вкладка, с которой пришли, — линза навбара переезжает с неё.
    private(set) var previousTab: Tab?
    /// Счётчик входов на вкладку. Навбар корневого экрана анимирует переход только
    /// один раз на каждый вход, а не при каждом onAppear (возврат с pushed-экрана,
    /// повторное появление в TabView).
    private(set) var tabEntry = 0
    /// Вкладка открылась сразу корневым экраном (без сохранённого стека).
    private(set) var entryAtRoot = true
    /// Дополнительная кнопка навбара каждой вкладки, какой она была на экране:
    /// навбар новой вкладки начинает анимацию с раскладки предыдущей.
    var navExtras: [Tab: GlassNavBar.Extra] = [:]
    /// У каждой вкладки системного TabView свой стек pushed-экранов.
    @Published var paths: [Tab: [Route]] = [:]
    @Published var authPath: [AuthRoute] = []
    /// «Поиск» на инфо-экране чата: возвращаемся в чат и открываем в нём поиск
    /// по сообщениям (как в Телеграме). Значение — id чата.
    @Published var chatSearchRequest: String? = nil

    init() {
        authed = TokenStore.isAuthenticated
        #if DEBUG
        applyDebugLaunchArgs()
        #endif
    }

    #if DEBUG
    /// Для сверки с макетом в симуляторе: `-kl_tab analytics`,
    /// `-kl_route report:1 | chat:<id> | chatInfo:<id> | profileEdit | notifications |
    /// accountData | subscription | admins | reportCreate`, `-kl_auth recovery | register`.
    private func applyDebugLaunchArgs() {
        let d = UserDefaults.standard
        // `-kl_chart <ссылка>`: сразу экран отчёта с этим графиком (проверка блока графика).
        if d.string(forKey: "kl_chart") != nil {
            authed = true
            paths[tab] = [.reportDetail(id: nil)]
            return
        }
        if let t = d.string(forKey: "kl_tab"), let tab = Tab(rawValue: t) { self.tab = tab }
        if let a = d.string(forKey: "kl_auth") {
            switch a {
            case "recovery": authPath = [.passwordRecovery]
            case "register": authPath = [.registerConfirm(email: "demo@karta.app")]
            case "profile": authPath = [.registerProfile]
            default: break
            }
        }
        guard let r = d.string(forKey: "kl_route") else { return }
        let parts = r.split(separator: ":", maxSplits: 1).map(String.init)
        let arg = parts.count > 1 ? parts[1] : ""
        let route: Route?
        switch parts[0] {
        case "report": route = .reportDetail(id: Int(arg))
        case "reportCreate": route = .reportCreate
        case "reportEdit": route = .reportEdit(id: Int(arg) ?? 1)
        case "chat": route = .chat(id: arg)
        case "chatInfo": route = .chatInfo(id: arg)
        case "chatEdit": route = .chatEdit(id: arg)
        case "profileEdit": route = .profileEdit
        case "notifications": route = .notifications
        case "accountData": route = .accountData
        case "accountChange": route = .accountChange(field: arg.isEmpty ? "password" : arg)
        case "accountSuccess": route = .accountSuccess(field: arg.isEmpty ? "password" : arg)
        case "admins": route = .admins
        case "subscription": route = .subscription
        case "agreement": route = .userAgreement
        default: route = nil
        }
        if let route { paths[tab] = [route] }
    }
    #endif

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

    /// Переключение вкладки из навбара (стек вкладки сохраняется).
    func selectTab(_ t: Tab) { tab = t }

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
