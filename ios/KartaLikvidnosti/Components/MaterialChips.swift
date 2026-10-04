import SwiftUI

/// Горизонтальная лента чипов материалов (home/MaterialChips.vue).
struct MaterialChips: View {
    @Binding var selected: String

    private struct Item { let key: String; let label: String; let icon: IconDef? }
    private let items: [Item] = [
        .init(key: "all", label: "Все", icon: nil),
        .init(key: "gold", label: "Золото", icon: AppIcons.matGold),
        .init(key: "silver", label: "Серебро", icon: AppIcons.matGold),
        .init(key: "platinum", label: "Платина", icon: AppIcons.matGold),
        .init(key: "wti", label: "Нефть WTI", icon: AppIcons.matOil),
        .init(key: "brent", label: "Нефть Brent", icon: AppIcons.matOil),
        .init(key: "usd", label: "USD", icon: AppIcons.matUsd),
        .init(key: "eur", label: "EUR", icon: AppIcons.matEur),
        .init(key: "gas", label: "Натуральный газ", icon: AppIcons.matGas),
        .init(key: "btc", label: "Биткоин", icon: AppIcons.matBtc),
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(items, id: \.key) { item in
                    Button {
                        selected = item.key
                    } label: {
                        HStack(spacing: 4) {
                            Text(item.label).font(.system(size: Theme.fontBase))
                            if let icon = item.icon {
                                Icon(icon, size: 16, color: Theme.text)
                            }
                        }
                        .foregroundStyle(Theme.text)
                        .padding(.horizontal, 12)
                        .frame(height: 48)
                        .background(selected == item.key ? AnyShapeStyle(Theme.gradientAccentPurple) : AnyShapeStyle(Color.clear),
                                    in: RoundedRectangle(cornerRadius: Theme.radiusChip, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(4)
        }
        .frame(height: 56)
        .background(Theme.surfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}
