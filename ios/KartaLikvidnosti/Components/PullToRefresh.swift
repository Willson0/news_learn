import SwiftUI

// Обновление «потянуть вниз». Корневые экраны заходят под статус-бар
// (ignoresSafeArea сверху), поэтому системный кружок UIRefreshControl рисуется
// под Dynamic Island тёмно-серым на тёмном фоне и его не видно. Системный
// оставляем ради жеста и удержания контента, но прячем (App.swift), а свой
// стеклянный кружок рисуем под статус-баром.

private enum PullSpace {
    static let name = "pullToRefresh"
    /// Сколько нужно протянуть, чтобы кружок нарисовался полностью.
    static let full: CGFloat = 90
    /// Кружок крутится не меньше этого, даже если ответ пришёл мгновенно.
    static let minimumSpin: TimeInterval = 0.7
}

private struct PullOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

extension View {
    /// Метка на содержимом ScrollView: по ней кружок знает, насколько экран оттянут.
    func pullToRefreshAnchor() -> some View {
        background(GeometryReader { g in
            Color.clear.preference(key: PullOffsetKey.self, value: g.frame(in: .named(PullSpace.name)).minY)
        })
    }

    /// Вместо `.refreshable` на ScrollView корневого экрана (содержимое помечено
    /// `pullToRefreshAnchor()`). Заодно перечитывает подписку: после оплаты
    /// обновление снимает режим «без подписки».
    func pullToRefresh(_ action: @escaping () async -> Void) -> some View {
        modifier(PullToRefresh(action: action))
    }
}

private struct PullToRefresh: ViewModifier {
    let action: () async -> Void

    @EnvironmentObject private var session: Session
    @State private var rest: CGFloat?
    @State private var pull: CGFloat = 0
    @State private var refreshing = false

    func body(content: Content) -> some View {
        content
            .coordinateSpace(name: PullSpace.name)
            .onPreferenceChange(PullOffsetKey.self) { y in
                if rest == nil { rest = y }
                pull = max(0, y - (rest ?? 0))
            }
            .refreshable { await run() }
            .overlay(alignment: .top) {
                RefreshIndicator(progress: min(pull / PullSpace.full, 1), spinning: refreshing)
                    .padding(.top, WindowInsets.top + 6 + min(pull, PullSpace.full) * 0.15)
                    .opacity(refreshing || pull > 4 ? 1 : 0)
                    .animation(.easeOut(duration: 0.2), value: refreshing)
                    .allowsHitTesting(false)
            }
    }

    private func run() async {
        refreshing = true
        Haptics.light()
        let start = Date()
        async let subscription: Void = session.loadSubscription()
        await action()
        await subscription
        let left = PullSpace.minimumSpin - Date().timeIntervalSince(start)
        if left > 0 { try? await Task.sleep(nanoseconds: UInt64(left * 1_000_000_000)) }
        refreshing = false
    }
}

/// Стеклянный кружок 40 с белой дугой: пока тянут — дуга дорисовывается,
/// во время обновления — крутится.
private struct RefreshIndicator: View {
    let progress: CGFloat
    let spinning: Bool

    var body: some View {
        TimelineView(.animation(paused: !spinning)) { ctx in
            let angle = spinning ? ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 0.8) / 0.8 * 360 : 0
            Circle()
                .trim(from: 0, to: spinning ? 0.75 : 0.75 * progress)
                .stroke(Theme.text, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .frame(width: 20, height: 20)
                .rotationEffect(.degrees(angle - 90 + (spinning ? 0 : Double(progress) * 270)))
        }
        .frame(width: 40, height: 40)
        .liquidGlass(Circle(), fill: Theme.surfaceMuted)
        .scaleEffect(spinning ? 1 : 0.6 + 0.4 * progress)
    }
}
