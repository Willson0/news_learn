import SwiftUI

struct RootView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    var body: some View {
        Group {
            if router.authed {
                MainFlow()
            } else {
                AuthFlow()
            }
        }
        .task { await session.load() }
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
                    }
                }
        }
    }
}

/// Основной поток приложения: корневые вкладки + стек pushed-экранов.
private struct MainFlow: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    var body: some View {
        NavigationStack(path: $router.path) {
            ZStack(alignment: .bottom) {
                base
                    .toolbar(.hidden, for: .navigationBar)
                BottomNav()
                    .padding(.bottom, 24)
            }
            .navigationDestination(for: Route.self) { route in
                destination(route)
            }
        }
    }

    @ViewBuilder private var base: some View {
        switch router.tab {
        case .home: HomeView()
        case .analytics: AnalyticsView()
        case .community: CommunityView()
        case .profile: ProfileView()
        }
    }

    @ViewBuilder private func destination(_ route: Route) -> some View {
        switch route {
        case .reportDetail(let id): ReportDetailView(reportId: id)
        case .reportCreate: ReportFormView(reportId: nil)
        case .reportEdit(let id): ReportFormView(reportId: id)
        case .chat(let id): ChatView(chatId: id)
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
        }
    }
}
