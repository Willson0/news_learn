import SwiftUI

/// Горизонтальная лента чипов материалов (home/MaterialChips.vue).
struct MaterialChips: View {
    @Binding var selected: String

    private struct Item { let key: String; let label: String; let icon: IconDef? }
    private let items: [Item] = [
        .init(key: "all", label: "Все", icon: nil),
        .init(key: "gold", label: "Золото", icon: AppIcons.matGold),
        .init(key: "silver", label: "Серебро", icon: AppIcons.matSilver),
        .init(key: "platinum", label: "Платина", icon: AppIcons.matPlat),
        .init(key: "wti", label: "Нефть WTI", icon: AppIcons.matOil),
        .init(key: "brent", label: "Нефть Brent", icon: AppIcons.matOil),
        .init(key: "usd", label: "USD", icon: AppIcons.matUsd),
        .init(key: "eur", label: "EUR", icon: AppIcons.matEur),
        .init(key: "gas", label: "Натуральный газ", icon: AppIcons.matGas),
        .init(key: "btc", label: "Биткоин", icon: AppIcons.matBtc),
    ]

    @Namespace private var indicator

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    ForEach(items, id: \.key) { item in
                        Button {
                            guard selected != item.key else { return }
                            Haptics.light()
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.78)) {
                                selected = item.key
                                proxy.scrollTo(item.key, anchor: .center)
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Text(item.label).font(Theme.sans(Theme.fontBase))
                                if let icon = item.icon {
                                    Icon(icon, size: 16, color: Theme.text)
                                }
                            }
                            .foregroundStyle(Theme.text)
                            .padding(.horizontal, 12)
                            .frame(minWidth: 48)
                            .frame(height: 48)
                            .background {
                                // Подсветка выбранного — одна на весь селектор и «переезжает»
                                // между пунктами с пружинной анимацией, как капсула системного таб-бара.
                                if selected == item.key {
                                    SelectionLens(radius: Theme.radiusChip)
                                        .matchedGeometryEffect(id: "lens", in: indicator)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .id(item.key)
                    }
                }
                .padding(4)
            }
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        }
        .frame(height: 56)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}
