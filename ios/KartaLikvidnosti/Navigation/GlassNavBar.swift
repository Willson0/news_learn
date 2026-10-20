import SwiftUI
import UIKit

/// Нижний навбар из макета («navbar»): стеклянная капсула 230×64, четыре
/// пункта 40×40 (x = 12/67/122/178), активный — стекло с заливкой «active»,
/// неактивные иконки — «белый неактив». Справа может стоять круглая стеклянная
/// кнопка 64 (админский «+» на «Аналитике», чат на «Профиле»); группа
/// центрируется. Под навбаром — затемнение 79 pt («затемнениние»).
///
/// Навбар рисуется внутри корневого экрана вкладки, поэтому при push/pop он
/// уезжает и возвращается вместе с экраном (в т.ч. на интерактивном свайпе).
struct GlassNavBar: View {
    /// Высота зоны home indicator текущего окна (0 на устройствах без неё).
    static var homeIndicatorInset: CGFloat {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
        return window?.safeAreaInsets.bottom ?? 0
    }

    var extra: Extra? = nil

    struct Extra {
        let icon: IconDef
        var iconSize: CGFloat = 30
        let action: () -> Void
    }

    @EnvironmentObject private var router: Router
    @EnvironmentObject private var session: Session
    /// Положение линзы: при появлении стартует с прошлой вкладки и переезжает
    /// на текущую.
    @State private var lensTab: Tab?
    @Namespace private var ns

    private static let allItems: [(tab: Tab, icon: IconDef, x: CGFloat)] = [
        (.home, AppIcons.navHome, 12),
        (.analytics, AppIcons.navAnalytics, 67),
        (.community, AppIcons.navCommunity, 122),
        (.profile, AppIcons.navProfile, 178),
    ]
    /// Без подписки (Figma 474:11853): навбар 120 — «Сообщество» и «Профиль».
    private static let restrictedItems: [(tab: Tab, icon: IconDef, x: CGFloat)] = [
        (.community, AppIcons.navCommunity, 12),
        (.profile, AppIcons.navProfile, 68),
    ]
    private var items: [(tab: Tab, icon: IconDef, x: CGFloat)] {
        session.restricted ? Self.restrictedItems : Self.allItems
    }
    private var barWidth: CGFloat { session.restricted ? 120 : 230 }

    var body: some View {
        HStack(spacing: 13) {
            bar
            if let extra, !session.restricted {
                Button {
                    Haptics.light()
                    extra.action()
                } label: {
                    Icon(extra.icon, size: extra.iconSize, color: Theme.text)
                        .frame(width: 64, height: 64)
                        .contentShape(Circle())
                        .liquidGlass(Circle(), fill: Theme.surfaceMuted, interactive: true)
                }
                .buttonStyle(PressableStyle())
            }
        }
        .padding(.bottom, 45)
        .frame(maxWidth: .infinity)
        .background(alignment: .bottom) {
            // Растушёвка доводится до самого низа экрана: высота и сдвиг на зону home indicator.
            let inset = Self.homeIndicatorInset
            Rectangle()
                .fill(Theme.bottomFade)
                .frame(height: 79 + inset)
                .offset(y: inset)
                .allowsHitTesting(false)
        }
        .onAppear {
            let from = router.previousTab ?? router.tab
            lensTab = from
            guard from != router.tab else { return }
            withAnimation(.spring(response: 0.42, dampingFraction: 0.78)) { lensTab = router.tab }
        }
    }

    private var bar: some View {
        ZStack(alignment: .topLeading) {
            Color.clear.frame(width: barWidth, height: 64)
            if let lensTab, let item = items.first(where: { $0.tab == lensTab }) {
                Color.clear
                    .frame(width: 40, height: 40)
                    .liquidGlass(Circle(), fill: ActiveFill())
                    .offset(x: item.x, y: 12)
            }
            ForEach(items, id: \.tab) { item in
                Button {
                    guard router.tab != item.tab else {
                        router.popToRoot()
                        return
                    }
                    Haptics.light()
                    router.selectTab(item.tab)
                } label: {
                    Icon(item.icon, size: 24, color: router.tab == item.tab ? Theme.text : Theme.textMuted)
                        .frame(width: 40, height: 40)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .offset(x: item.x, y: 12)
            }
        }
        .frame(width: barWidth, height: 64)
        .liquidGlass(Capsule(), fill: ZStack { Theme.navbarBg; Theme.surfaceMuted })
    }
}

extension View {
    /// Корневой экран вкладки: навбар поверх контента у нижнего края.
    func withGlassNavBar(extra: GlassNavBar.Extra? = nil) -> some View {
        // Контент экрана заходит в зону home indicator: лента доходит до края экрана,
        // а отступ под навбар даёт navBarClearance внутри прокрутки.
        ignoresSafeArea(.container, edges: .bottom)
            .hidesBottomScrollEdge()
            .overlay(alignment: .bottom) {
                GlassNavBar(extra: extra).ignoresSafeArea(.container, edges: .bottom)
            }
    }

    /// iOS 26 рисует системное затемнение у нижнего края прокрутки, оно видно полосой
    /// над home indicator. Низ экрана уже закрыт нашей растушёвкой навбара, поэтому
    /// системное затемнение отключаем.
    @ViewBuilder func hidesBottomScrollEdge() -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.scrollEdgeEffectHidden(true, for: .bottom)
        } else {
            self
        }
        #else
        self
        #endif
    }
}
