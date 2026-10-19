import SwiftUI

struct FiltersResult {
    let instruments: [String]
    let statuses: [String]
    let dateMode: String
}

/// Лист фильтров «Аналитика фильтры». Показывается поверх экрана с
/// затемнением (см. `FiltersOverlay`).
struct FiltersSheet: View {
    let onApply: (FiltersResult) -> Void
    let onClose: () -> Void

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
    @State private var dateStart: String? = nil
    @State private var dateEnd: String? = nil
    @State private var calendarOpen = false

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

    @State private var dragY: CGFloat = 0

    // Лист фильтров из макета: плашка «фон плашки» от y=172 до низа, радиус 24,
    // полоска-хват 32 на y=183; «Искать» (Button/искать) справа сверху;
    // заголовки Kreadon 24; блоки «Фон плашки 2» с чипами; календарь — стеклянный
    // поп-ап над блоками.
    var body: some View {
        ZStack(alignment: .topLeading) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Инструменты").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        .padding(.top, 35)
                    chipCard($instruments).padding(.top, 24)

                    Text("По статусу").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        .padding(.top, 40)
                    chipCard($statuses, hPadding: 24).padding(.top, 24)

                    HStack(alignment: .center) {
                        Text("По дате").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        Spacer()
                        if !rangeLabel.isEmpty {
                            // Выбранный диапазон (Figma 474:15508): крестик 12 в
                            // области 38×32, пилюля 32 со стеклом «active», 14 Regular.
                            HStack(spacing: 4) {
                                Button {
                                    Haptics.light()
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                                        dateStart = nil
                                        dateEnd = nil
                                    }
                                } label: {
                                    Icon(AppIcons.close, size: 12, color: Theme.text)
                                        .frame(width: 38, height: 32)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                Text(rangeLabel)
                                    .font(Theme.sans(Theme.fontBase))
                                    .foregroundStyle(Theme.text)
                                    .padding(.horizontal, 12)
                                    .frame(height: 32)
                                    .liquidGlass(Capsule(), fill: ActiveFill())
                            }
                            .padding(.trailing, 8)
                            .transition(.opacity)
                        }
                        Button {
                            Haptics.light()
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { calendarOpen.toggle() }
                        } label: {
                            Icon(AppIcons.calendarNumber, size: 32, color: Theme.text)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 40)

                    SegmentedControl(selection: $dateMode, options: [
                        ("week", "Неделя"), ("month", "Месяц"), ("year", "Год"),
                    ])
                    .padding(.top, 24)
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.bottom, 60)
            }

            if calendarOpen {
                VStack(spacing: 8) {
                    AppCalendar(start: $dateStart, end: $dateEnd)
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.top, 87)
                .transition(.scale(scale: 0.92, anchor: .bottomTrailing).combined(with: .opacity))
            }

            HStack {
                Spacer()
                PillActionButton(title: "Искать", action: apply)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 12)

            Capsule().fill(Color.white).frame(width: 32, height: 1.5)
                .frame(maxWidth: .infinity)
                .padding(.top, 11)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.surface, in: UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous))
        .ignoresSafeArea(.container, edges: .bottom)
        .offset(y: max(dragY, 0))
        .gesture(
            DragGesture()
                .onChanged { dragY = $0.translation.height }
                .onEnded { v in
                    if v.translation.height > 120 || v.predictedEndTranslation.height > 260 { onClose() }
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dragY = 0 }
                }
        )
    }

    @ViewBuilder private func chipCard(_ items: Binding<[InstrumentDto]>, hPadding: CGFloat = 14) -> some View {
        FlowLayout(spacing: 4, lineSpacing: 4) {
            ForEach(items.wrappedValue.indices, id: \.self) { i in
                InstrumentChip(label: items.wrappedValue[i].label, on: items[i].on, hPadding: hPadding)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface2Plate, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func apply() {
        Haptics.medium()
        onApply(FiltersResult(
            instruments: instruments.filter { $0.on }.map { $0.key },
            statuses: statuses.filter { $0.on }.map { $0.key },
            dateMode: dateMode
        ))
        onClose()
    }
}

/// Затемнение rgba(7,7,7,0.71) с размытием 6.45 и лист фильтров от y=172.
struct FiltersOverlay: View {
    @Binding var isOpen: Bool
    let onApply: (FiltersResult) -> Void

    var body: some View {
        ZStack(alignment: .top) {
            if isOpen {
                ZStack {
                    BackdropBlur(radius: 6.45)
                    Color(hex: "#070707").opacity(0.71)
                }
                .ignoresSafeArea()
                .onTapGesture { close() }
                .transition(.opacity)

                FiltersSheet(onApply: onApply, onClose: close)
                    .padding(.top, 172)
                    .transition(.move(edge: .bottom))
            }
        }
        .ignoresSafeArea(.container, edges: .top)
        .animation(.spring(response: 0.42, dampingFraction: 0.9), value: isOpen)
    }

    private func close() { isOpen = false }
}
