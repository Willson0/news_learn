import SwiftUI

/// Применённые фильтры. Пустой список или `nil` означает «по этому фильтру ничего не фильтруем».
struct FiltersResult {
    let instruments: [String]
    let statuses: [String]
    let dateMode: String?
    /// Произвольный диапазон (YYYY-MM-DD); без него действует период `dateMode`.
    let dateStart: String?
    let dateEnd: String?
}

/// Лист фильтров «Аналитика фильтры». Показывается поверх экрана с
/// затемнением (см. `FiltersOverlay`).
struct FiltersSheet: View {
    /// Уже применённые фильтры: лист открывается с ними, а не со сброшенным состоянием.
    let initial: FiltersResult
    let onApply: (FiltersResult) -> Void
    let onClose: () -> Void

    @State private var instruments: [InstrumentDto]
    @State private var statuses: [InstrumentDto]
    @State private var dateMode: String?
    @State private var dateStart: String?
    @State private var dateEnd: String?
    @State private var calendarOpen = false

    private static let instrumentOptions: [(key: String, label: String)] = [
        ("gold", "Золото"), ("silver", "Серебро"), ("platinum", "Платина"), ("wti", "Нефть WTI"),
        ("brent", "Нефть Brent"), ("eurusd", "EUR/USD"), ("gas", "Нат. газ"), ("btc", "Биткоин"),
    ]
    private static let statusOptions: [(key: String, label: String)] = [
        ("hot", "С пылу с жару"), ("actual", "Актуальное"), ("inactual", "Не актуально"), ("archive", "Архивное"),
    ]

    init(initial: FiltersResult, onApply: @escaping (FiltersResult) -> Void, onClose: @escaping () -> Void) {
        self.initial = initial
        self.onApply = onApply
        self.onClose = onClose
        _instruments = State(initialValue: Self.options(Self.instrumentOptions, selected: initial.instruments))
        _statuses = State(initialValue: Self.options(Self.statusOptions, selected: initial.statuses))
        _dateMode = State(initialValue: initial.dateMode)
        _dateStart = State(initialValue: initial.dateStart)
        _dateEnd = State(initialValue: initial.dateEnd)
    }

    private static func options(_ list: [(key: String, label: String)], selected: [String]) -> [InstrumentDto] {
        list.map { InstrumentDto(key: $0.key, label: $0.label, on: selected.contains($0.key)) }
    }

    private var dateModeBinding: Binding<String> {
        Binding(get: { dateMode ?? "" }, set: { dateMode = $0 })
    }

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

                    SegmentedControl(selection: dateModeBinding, options: [
                        ("week", "Неделя"), ("month", "Месяц"), ("year", "Год"),
                    ], highlight: rangeLabel.isEmpty)
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
            dateMode: dateMode,
            dateStart: dateStart,
            dateEnd: dateEnd
        ))
        onClose()
    }
}

/// Затемнение rgba(7,7,7,0.71) с размытием 6.45 и лист фильтров от y=172.
struct FiltersOverlay: View {
    @Binding var isOpen: Bool
    let applied: FiltersResult
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

                FiltersSheet(initial: applied, onApply: onApply, onClose: close)
                    .padding(.top, 172)
                    .transition(.move(edge: .bottom))
            }
        }
        .ignoresSafeArea(.container, edges: .top)
        // Прототип: «Аналитика» → Chips → оверлей «Frame 287»: MOVE_IN снизу,
        // пружина SLOW (≈1.25 с), затемнение под листом.
        .animation(Motion.slow, value: isOpen)
    }

    private func close() { isOpen = false }
}
