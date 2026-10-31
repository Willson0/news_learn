import SwiftUI

struct RootView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    @EnvironmentObject var appData: AppData

    var body: some View {
        Group {
            if router.authed {
                MainFlow()
            } else {
                AuthFlow()
            }
        }
        .appToastHost()
        // Клавиатура: тап мимо поля и прокрутка вниз убирают её на любом экране.
        .dismissesKeyboardOnTap()
        .scrollDismissesKeyboard(.interactively)
        .task {
            await session.load()
            await appData.preload()
        }
        // Сервер не принял токен (истёк или отозван): на вход с пояснением, без вылета.
        .onReceive(NotificationCenter.default.publisher(for: .sessionExpired)) { _ in
            guard router.authed else { return }
            session.clear()
            router.logout()
            AppToast.shared.show(ApiError.expiredText)
        }
        .onChange(of: router.authed) { authed in
            Task {
                if authed { await appData.preload() } else { appData.clear() }
            }
        }
    }
}

/// Поток авторизации: вход → подтверждение почты / восстановление пароля.
private struct AuthFlow: View {
    @EnvironmentObject var router: Router

    var body: some View {
        NavigationStack(path: $router.authPath) {
            LoginView()
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .registerConfirm(let email):
                        RegisterConfirmView(email: email).navigationBarBackButtonHidden(false)
                    case .passwordRecovery:
                        PasswordRecoveryView()
                    case .registerProfile:
                        RegisterProfileView().navigationBarBackButtonHidden(true)
                    }
                }
        }
        .onChange(of: router.authPath) { _ in KeyboardDismiss.hide() }
    }
}

/// Основной поток приложения: TabView со скрытым системным таб-баром — у
/// каждой вкладки свой стек pushed-экранов, а навбар из макета (стеклянная
/// капсула) рисуется внутри корневого экрана вкладки и уезжает вместе с ним.
private struct MainFlow: View {
    @EnvironmentObject var router: Router

    var body: some View {
        TabView(selection: $router.tab) {
            stack(.home) { HomeView() }.tag(Tab.home)
            stack(.analytics) { AnalyticsView() }.tag(Tab.analytics)
            stack(.community) { CommunityView() }.tag(Tab.community)
            stack(.profile) { ProfileView() }.tag(Tab.profile)
        }
        .tint(Theme.text)
        .onChange(of: session.restricted) { _ in enforceRestriction() }
        .onChange(of: router.tab) { _ in enforceRestriction(); KeyboardDismiss.hide() }
        // Клавиатура не переживает переход между экранами (в том числе свайп назад):
        // при возврате, например, из инфо чата она не всплывает и не закрывает экран.
        .onChange(of: router.paths) { _ in KeyboardDismiss.hide() }
        .onAppear(perform: enforceRestriction)
    }

    @EnvironmentObject private var session: Session

    /// Без подписки главная и аналитика недоступны — открываем «Профиль» с оплатой.
    private func enforceRestriction() {
        guard session.restricted, router.tab == .home || router.tab == .analytics else { return }
        router.switchTab(.profile)
    }

    private func stack<Content: View>(_ t: Tab, @ViewBuilder _ root: () -> Content) -> some View {
        NavigationStack(path: router.path(for: t)) {
            root()
                .environment(\.hostTab, t)
                .toolbar(.hidden, for: .navigationBar)
                .toolbar(.hidden, for: .tabBar)
                .navigationDestination(for: Route.self) { route in
                    destination(route)
                        .toolbar(.hidden, for: .tabBar)
                }
        }
        .toolbar(.hidden, for: .tabBar)
    }

    /// Внутренние экраны: системная панель скрыта, у экрана своя стеклянная
    /// кнопка «назад» (GlassTopBar). Чат пока со своей шапкой в toolbar.
    @ViewBuilder private func destination(_ route: Route) -> some View {
        switch route {
        case .chat(let id):
            ChatView(chatId: id)
                .toolbar(.hidden, for: .navigationBar)
        case .reportDetail, .profileEdit, .chatInfo, .chatUser, .chatEdit, .reportCreate, .reportEdit:
            screen(route)
                .toolbar(.hidden, for: .navigationBar)
        default:
            screen(route)
                .glassTopBar()
                .toolbar(.hidden, for: .navigationBar)
        }
    }

    @ViewBuilder private func screen(_ route: Route) -> some View {
        switch route {
        case .reportDetail(let id): ReportDetailView(reportId: id)
        case .reportCreate: ReportFormView(reportId: nil)
        case .reportEdit(let id): ReportFormView(reportId: id)
        case .chat: EmptyView()
        case .chatInfo(let id): ChatInfoView(chatId: id)
        case .chatEdit(let id): ChatEditView(chatId: id)
        case .chatUser(let id, let uid): UserProfileView(chatId: id, uid: uid)
        case .profileEdit: ProfileEditView()
        case .notifications: NotificationsView()
        case .accountData: AccountDataView()
        case .accountChange(let field): AccountChangeView(field: field)
        case .accountSuccess(let field): AccountSuccessView(field: field)
        case .admins: AdminsView()
        case .subscription: SubscriptionView()
        case .userAgreement: UserAgreementView()
        }
    }
}
