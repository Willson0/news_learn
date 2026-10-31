import SwiftUI
import UIKit

/// Нижний навбар из макета («navbar»): стеклянная капсула 230×64, четыре
/// пункта 40×40 (x = 12/67/122/178), активный — стекло с заливкой «active»,
/// неактивные иконки — «белый неактив». Справа может стоять круглая стеклянная
/// кнопка 64 (админский «+» на «Аналитике», чат на «Профиле»); группа
/// центрируется (Figma «Профиль» 484:21226: капсула x=44, кнопка x=286).
/// Под навбаром — затемнение 79 pt («затемнениние»).
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
        /// Подпись для VoiceOver: «Создать отчет», «Чат с администратором».
        let label: String
        var iconSize: CGFloat = 30
        let action: () -> Void
    }

    @EnvironmentObject private var router: Router
    @EnvironmentObject private var session: Session
    @Environment(\.hostTab) private var hostTab

    /// Вкладка, на которой стоит линза и подсвечена иконка. При входе на вкладку
    /// стартует с прошлой и переезжает на текущую.
    @State private var lensTab: Tab?
    /// Сдвиг капсулы относительно итоговой раскладки: при входе на «Профиль» капсула
    /// уезжает из центра влево, освобождая место кнопке чата, и обратно.
    @State private var barShift: CGFloat = 0
    /// Прозрачность кнопки справа (кнопки нет в раскладке прошлой вкладки — проявляется).
    @State private var extraOpacity: Double = 1
    /// Кнопка прошлой вкладки, которая растворяется на месте (или иконка, которая
    /// растворяется внутри кнопки, если кнопка есть на обеих вкладках).
    @State private var ghost: Extra?
    @State private var ghostOpacity: Double = 0
    /// Вход на вкладку, для которого уже проиграна анимация.
    @State private var handledEntry = -1

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
    private var current: Tab { hostTab ?? router.tab }
    private var shownExtra: Extra? { session.restricted ? nil : extra }

    /// Половина ширины кнопки 64 и зазора 13: на столько капсула смещается из центра.
    private static let extraShift: CGFloat = (64 + 13) / 2
    /// Центр кнопки справа от центра экрана, когда группа «капсула + кнопка» по центру.
    private static let extraCenterX: CGFloat = (230 + 13 + 64) / 2 - 32

    var body: some View {
        ZStack {
            HStack(spacing: 13) {
                bar.offset(x: barShift)
                if let extra = shownExtra {
                    extraButton(extra)
                        .opacity(extraOpacity)
                }
            }
            // Кнопка прошлой вкладки растворяется там, где стояла (Figma SMART_ANIMATE:
            // слой без пары на новом кадре исчезает на месте).
            if shownExtra == nil, let ghost {
                extraLabel(ghost)
                    .opacity(ghostOpacity)
                    .offset(x: Self.extraCenterX)
                    .allowsHitTesting(false)
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
        .onAppear(perform: playEntry)
        .onChange(of: session.restricted) { _ in router.navExtras[current] = shownExtra }
    }

    /// Переход между вкладками в прототипе — SMART_ANIMATE 800 мс, ease-in-out-back:
    /// линза и подсветка иконки переезжают с прошлой вкладки, капсула сдвигается под
    /// новую раскладку, кнопка справа проявляется (или растворяется кнопка прошлой
    /// вкладки). Проигрывается один раз на вход на вкладку: при возврате с
    /// pushed-экрана навбар сразу стоит в итоговом состоянии.
    private func playEntry() {
        let me = current
        let mine = shownExtra
        defer { router.navExtras[me] = mine }

        let isNewEntry = router.tabEntry != handledEntry
        handledEntry = router.tabEntry
        let from = router.previousTab ?? me
        // «Уменьшение движения»: навбар сразу в итоговом виде, без выезда и линзы.
        guard isNewEntry, router.entryAtRoot, router.tab == me, from != me, !UIAccessibility.isReduceMotionEnabled else {
            settle()
            return
        }
        let prev = router.navExtras[from]

        var start = Transaction()
        start.disablesAnimations = true
        withTransaction(start) {
            lensTab = from
            switch (prev, mine) {
            case (nil, .some(_)):
                barShift = Self.extraShift
                extraOpacity = 0
                ghost = nil
            case (.some(let p), nil):
                barShift = -Self.extraShift
                extraOpacity = 1
                ghost = p
                ghostOpacity = 1
            case (.some(let p), .some(_)):
                barShift = 0
                extraOpacity = 1
                ghost = p
                ghostOpacity = 1
            case (nil, nil):
                barShift = 0
                extraOpacity = 1
                ghost = nil
            }
        }
        // Итоговое состояние — в следующей транзакции: изменения, сделанные в том же
        // проходе, что и появление вкладки, SwiftUI склеивает без анимации.
        DispatchQueue.main.async {
            withAnimation(Motion.standard) {
                lensTab = me
                barShift = 0
                extraOpacity = 1
                ghostOpacity = 0
            }
        }
    }

    private func settle() {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t) {
            lensTab = current
            barShift = 0
            extraOpacity = 1
            ghost = nil
            ghostOpacity = 0
        }
    }

    private func extraButton(_ extra: Extra) -> some View {
        Button {
            Haptics.light()
            extra.action()
        } label: {
            ZStack {
                // Кнопка есть на обеих вкладках — меняется только иконка (растворением).
                if let ghost {
                    Icon(ghost.icon, size: ghost.iconSize, color: Theme.text)
                        .opacity(ghostOpacity)
                }
                Icon(extra.icon, size: extra.iconSize, color: Theme.text)
                    .opacity(ghost == nil ? 1 : 1 - ghostOpacity)
            }
            .frame(width: 64, height: 64)
            .contentShape(Circle())
            .liquidGlass(Circle(), fill: Theme.surfaceMuted, interactive: true)
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel(extra.label)
    }

    private func extraLabel(_ extra: Extra) -> some View {
        Icon(extra.icon, size: extra.iconSize, color: Theme.text)
            .frame(width: 64, height: 64)
            .liquidGlass(Circle(), fill: Theme.surfaceMuted)
    }

    private var bar: some View {
        ZStack(alignment: .topLeading) {
            Color.clear.frame(width: barWidth, height: 64)
            if let item = items.first(where: { $0.tab == (lensTab ?? current) }) {
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
                    Icon(item.icon, size: 24, color: (lensTab ?? current) == item.tab ? Theme.text : Theme.iconMuted)
                        .frame(width: 40, height: 40)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                // VoiceOver: «Главная, вкладка, выбрано».
                .accessibilityLabel(item.tab.label)
                .accessibilityAddTraits((lensTab ?? current) == item.tab ? [.isSelected] : [])
                .offset(x: item.x, y: 12)
            }
        }
        .frame(width: barWidth, height: 64)
        .liquidGlass(Capsule(), fill: ZStack { Theme.navbarBg; Theme.surfaceMuted })
    }
}

/// Вкладка, корневым экраном которой является вью (задаётся в MainFlow).
private struct HostTabKey: EnvironmentKey {
    static let defaultValue: Tab? = nil
}

extension EnvironmentValues {
    var hostTab: Tab? {
        get { self[HostTabKey.self] }
        set { self[HostTabKey.self] = newValue }
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
