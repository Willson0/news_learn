import SwiftUI

/// Плавающая нижняя навигация (BottomNav.vue).
struct BottomNav: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    private struct TabItem { let tab: Tab; let icon: IconDef }
    private let tabs: [TabItem] = [
        .init(tab: .home, icon: AppIcons.home),
        .init(tab: .analytics, icon: AppIcons.analytics),
        .init(tab: .community, icon: AppIcons.community),
        .init(tab: .profile, icon: AppIcons.profile),
    ]

    private var showCreate: Bool { session.isAdmin && router.tab == .analytics }

    var body: some View {
        HStack(spacing: 13) {
            HStack(spacing: 15) {
                ForEach(tabs, id: \.tab) { item in
                    Button {
                        Haptics.light()
                        router.switchTab(item.tab)
                    } label: {
                        Icon(item.icon, size: 24, color: Theme.text)
                            .frame(width: 40, height: 40)
                            .background(router.tab == item.tab ? AnyShapeStyle(Theme.gradientAccentSoft) : AnyShapeStyle(Color.clear),
                                        in: Capsule())
                            .opacity(router.tab == item.tab ? 1 : 0.6)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(12)
            .background(.ultraThinMaterial, in: Capsule())
            .environment(\.colorScheme, .dark)

            if showCreate {
                Button {
                    Haptics.light()
                    router.push(.reportCreate)
                } label: {
                    Icon(AppIcons.plus, size: 30, color: Theme.text)
                        .frame(width: 62, height: 62)
                        .background(Theme.gradientAccent, in: Circle())
                        .shadow(color: .black.opacity(0.35), radius: 10, y: 6)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
