import SwiftUI

struct FiltersResult {
    let instruments: [String]
    let statuses: [String]
    let dateMode: String
}

/// Нижний лист фильтров аналитики (analytics/FiltersSheet.vue).
struct FiltersSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onApply: (FiltersResult) -> Void

    @State private var instruments: [InstrumentDto] = [
        .init(key: "gold", label: "Золото", on: true),
        .init(key: "silver", label: "Серебро", on: false),
        .init(key: "platinum", label: "Платина", on: true),
        .init(key: "wti", label: "Нефть WTI", on: false),
        .init(key: "brent", label: "Нефть Brent", on: true),
        .init(key: "eurusd", label: "EUR/USD", on: false),
        .init(key: "gas", label: "Нат. газ", on: false),
        .init(key: "btc", label: "Биткоин", on: false),
    ]
    @State private var statuses: [InstrumentDto] = [
        .init(key: "hot", label: "С пылу с жару", on: true),
        .init(key: "actual", label: "Актуальное", on: true),
        .init(key: "inactual", label: "Не актуально", on: true),
        .init(key: "archive", label: "Архивное", on: false),
    ]
    @State private var dateMode = "week"
    @State private var dateStart: String? = "2026-03-17"
    @State private var dateEnd: String? = "2026-03-28"
    @State private var calendarOpen = true

    private static let shortMonths = ["ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН",
                                      "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК"]

    private static func shortDate(_ iso: String?) -> String? {
        guard let iso = iso else { return nil }
        let p = iso.split(separator: "-")
        guard p.count == 3, let m = Int(p[1]), (1...12).contains(m), let d = Int(p[2]) else { return nil }
        return "\(d) \(shortMonths[m - 1])"
    }

    /// Подпись выбранного диапазона дат: «С 17 МАР - 28 МАР» или «С 17 МАР».
    private var rangeLabel: String {
        guard let s = Self.shortDate(dateStart) else { return "" }
        if let e = Self.shortDate(dateEnd) { return "С \(s) - \(e)" }
        return "С \(s)"
    }

    var body: some View {
        VStack(spacing: 0) {
            Capsule().fill(Color.white.opacity(0.3)).frame(width: 40, height: 4)
                .padding(.top, 10).padding(.bottom, 12)

            HStack {
                Text("Инструменты").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                Spacer()
                Button(action: apply) {
                    Text("Искать").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 22).padding(.vertical, 10)
                        .background(Theme.gradientAccent).clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.bottom, 16)

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    chipCard($instruments)

                    Text("По статусу").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    chipCard($statuses)

                    HStack {
                        Text("По дате").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        Spacer()
                        if !rangeLabel.isEmpty {
                            Button { dateStart = nil; dateEnd = nil; calendarOpen = true } label: {
                                Icon(AppIcons.close, size: 18, color: Theme.textSecondary)
                            }.buttonStyle(.plain)
                            Text(rangeLabel).font(.system(size: Theme.fontSm))
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(Theme.gradientAccent).clipShape(Capsule())
                        }
                        Button { calendarOpen.toggle() } label: {
                            Icon(AppIcons.calendar, size: 24, color: Theme.text)
                        }.buttonStyle(.plain)
                    }

                    if calendarOpen {
                        AppCalendar(start: $dateStart, end: $dateEnd)
                    }

                    SegmentedControl(selection: $dateMode, options: [
                        ("week", "Неделя"), ("month", "Месяц"), ("year", "Год"),
                    ])
                }
                .padding(.bottom, 20)
            }
        }
        .padding(.horizontal, Theme.screenX)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(hex: "#1c1d1f").ignoresSafeArea())
    }

    @ViewBuilder private func chipCard(_ items: Binding<[InstrumentDto]>) -> some View {
        FlowLayout(spacing: 8) {
            ForEach(items.wrappedValue.indices, id: \.self) { i in
                InstrumentChip(label: items.wrappedValue[i].label, on: items[i].on)
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func apply() {
        Haptics.medium()
        onApply(FiltersResult(
            instruments: instruments.filter { $0.on }.map { $0.key },
            statuses: statuses.filter { $0.on }.map { $0.key },
            dateMode: dateMode
        ))
        dismiss()
    }
}
