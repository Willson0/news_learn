import SwiftUI

/// Применённые фильтры. Пустой список или `nil` означает «по этому фильтру ничего не фильтруем».
struct FiltersResult: Equatable {
    var instruments: [String] = []
    var statuses: [String] = []
    var dateMode: String? = nil
    /// Произвольный диапазон (YYYY-MM-DD); без него действует период `dateMode`.
    var dateStart: String? = nil
    var dateEnd: String? = nil

    /// Режим «Свои даты»: период задан диапазоном в календаре.
    static let customDates = "custom"
    static let periodOptions: [(value: String, label: String)] = [("week", "Неделя"), ("month", "Месяц"), ("year", "Год")]
    static let instrumentOptions: [(key: String, label: String)] = [
        ("gold", "Золото"), ("silver", "Серебро"), ("platinum", "Платина"), ("wti", "Нефть WTI"),
        ("brent", "Нефть Brent"), ("eurusd", "EUR/USD"), ("gas", "Нат. газ"), ("btc", "Биткоин"),
    ]
    static let statusOptions: [(key: String, label: String)] = [
        ("hot", "С пылу с жару"), ("actual", "Актуальное"), ("inactual", "Не актуально"), ("archive", "Архивное"),
    ]

    var hasRange: Bool { dateStart != nil && dateEnd != nil }

    /// Один выбор даты: период или диапазон. Незаконченный диапазон не фильтрует.
    var normalized: FiltersResult {
        var r = self
        if hasRange {
            r.dateMode = Self.customDates
        } else {
            r.dateMode = Self.periodOptions.contains { $0.value == dateMode } ? dateMode : nil
            r.dateStart = nil
            r.dateEnd = nil
        }
        return r
    }

    /// Период для API (неделя / месяц / год) — только если не выбран диапазон.
    var period: String? { normalized.dateMode == Self.customDates ? nil : normalized.dateMode }

    /// Число включённых фильтров — бейдж на кнопке фильтров.
    var activeCount: Int {
        let n = normalized
        return n.instruments.count + n.statuses.count + (n.dateMode != nil ? 1 : 0)
    }

    /// Снимаемые чипы над списком: id → подпись («Золото», «Месяц», «С 12 ОКТ - 20 ОКТ»).
    var activeChips: [(id: String, label: String)] {
        let n = normalized
        var list: [(id: String, label: String)] = []
        for k in n.instruments { list.append((id: "i:\(k)", label: Self.instrumentOptions.first { $0.key == k }?.label ?? k)) }
        for k in n.statuses { list.append((id: "s:\(k)", label: Self.statusOptions.first { $0.key == k }?.label ?? k)) }
        if let mode = n.dateMode {
            if mode == Self.customDates {
                list.append((id: "d", label: "С \(FiltersSheet.shortDate(n.dateStart) ?? "") - \(FiltersSheet.shortDate(n.dateEnd) ?? "")"))
            } else {
                list.append((id: "d", label: Self.periodOptions.first { $0.value == mode }?.label ?? mode))
            }
        }
        return list
    }

    /// Фильтры без одного чипа (крестик на чипе над списком).
    func without(_ chipId: String) -> FiltersResult {
        var r = self
        if chipId.hasPrefix("i:") {
            r.instruments.removeAll { $0 == String(chipId.dropFirst(2)) }
        } else if chipId.hasPrefix("s:") {
            r.statuses.removeAll { $0 == String(chipId.dropFirst(2)) }
        } else {
            r.dateMode = nil; r.dateStart = nil; r.dateEnd = nil
        }
        return r.normalized
    }
}

/// «материал / материала / материалов».
func materialsCount(_ n: Int) -> String {
    let mod100 = n % 100, mod10 = n % 10
    let word: String
    if (11...14).contains(mod100) { word = "материалов" }
    else if mod10 == 1 { word = "материал" }
    else if (2...4).contains(mod10) { word = "материала" }
    else { word = "материалов" }
    return "\(n) \(word)"
}

/// Лист фильтров «Аналитика фильтры». Показывается поверх экрана с
/// затемнением (см. `FiltersOverlay`).
struct FiltersSheet: View {
    /// Уже применённые фильтры: лист открывается с ними, а не со сброшенным состоянием.
    let initial: FiltersResult
    let onApply: (FiltersResult) -> Void
    let onClose: () -> Void
    /// Сколько материалов найдётся с такими фильтрами (nil — неизвестно).
    var countFor: ((FiltersResult) async -> Int?)? = nil
    /// Текущий выбор в листе — чтобы применить его при закрытии свайпом.
    var onDraftChange: (FiltersResult) -> Void = { _ in }

    @State private var count: Int? = nil
    @State private var counting = false

    @State private var instruments: [InstrumentDto]
    @State private var statuses: [InstrumentDto]
    @State private var dateMode: String?
    @State private var dateStart: String?
    @State private var dateEnd: String?
    @State private var calendarOpen = false

    init(initial: FiltersResult, onApply: @escaping (FiltersResult) -> Void, onClose: @escaping () -> Void,
         countFor: ((FiltersResult) async -> Int?)? = nil, onDraftChange: @escaping (FiltersResult) -> Void = { _ in }) {
        self.initial = initial
        self.onApply = onApply
        self.onClose = onClose
        self.countFor = countFor
        self.onDraftChange = onDraftChange
        _instruments = State(initialValue: Self.options(FiltersResult.instrumentOptions, selected: initial.instruments))
        _statuses = State(initialValue: Self.options(FiltersResult.statusOptions, selected: initial.statuses))
        _dateMode = State(initialValue: initial.normalized.dateMode)
        _dateStart = State(initialValue: initial.dateStart)
        _dateEnd = State(initialValue: initial.dateEnd)
    }

    private static func options(_ list: [(key: String, label: String)], selected: [String]) -> [InstrumentDto] {
        list.map { InstrumentDto(key: $0.key, label: $0.label, on: selected.contains($0.key)) }
    }

    /// Период и диапазон — один выбор: «Свои даты» открывает календарь, период сбрасывает диапазон.
    private var dateModeBinding: Binding<String> {
        Binding(get: { dateMode ?? "" }, set: { v in
            dateMode = v
            if v == FiltersResult.customDates {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { calendarOpen = true }
            } else {
                dateStart = nil; dateEnd = nil
                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { calendarOpen = false }
            }
        })
    }

    private var current: FiltersResult {
        FiltersResult(
            instruments: instruments.filter { $0.on }.map { $0.key },
            statuses: statuses.filter { $0.on }.map { $0.key },
            dateMode: dateMode,
            dateStart: dateStart,
            dateEnd: dateEnd
        ).normalized
    }

    private var applyTitle: String {
        guard countFor != nil, let count else { return "Показать материалы" }
        return count == 0 ? "Ничего не найдено" : "Показать \(materialsCount(count))"
    }

    private static let shortMonths = ["ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН",
                                      "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК"]

    static func shortDate(_ iso: String?) -> String? {
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
    /// Список прокручен к самому верху: тогда протяжка вниз тянет лист, а не список.
    @State private var scrollAtTop = true
    /// Решение на весь жест, принятое в его начале: nil — жест ещё не начат.
    @State private var dragsSheet: Bool?

    // Лист фильтров из макета: плашка «фон плашки» от y=172 до низа, радиус 24,
    // полоска-хват 32 на y=183; «Искать» (Button/искать) справа сверху;
    // заголовки Kreadon 24; блоки «Фон плашки 2» с чипами; календарь — стеклянный
    // поп-ап над блоками.
    var body: some View {
        ZStack(alignment: .topLeading) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    GeometryReader { g in
                        Color.clear.preference(key: SheetScrollTopKey.self,
                                               value: g.frame(in: .named("filtersScroll")).minY)
                    }
                    .frame(height: 0)
                    Text("Инструменты").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        .padding(.top, 35)
                    chipCard($instruments).padding(.top, 24)

                    Text("По статусу").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        .padding(.top, 40)
                    chipCard($statuses, hPadding: 24).padding(.top, 24)

                    HStack(alignment: .center) {
                        Text("По дате").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                        Spacer()
                        if dateMode == FiltersResult.customDates && !rangeLabel.isEmpty {
                            // Выбранный диапазон (Figma 474:15508): крестик 12 в области 44,
                            // пилюля 32 со стеклом «active», 14 Regular; нажатие на пилюлю — календарь.
                            HStack(spacing: 0) {
                                Button {
                                    Haptics.light()
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                                        dateStart = nil
                                        dateEnd = nil
                                        dateMode = nil
                                        calendarOpen = false
                                    }
                                } label: {
                                    Icon(AppIcons.close, size: 12, color: Theme.text)
                                        .frame(width: 44, height: 44)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Убрать диапазон дат")
                                Button {
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { calendarOpen.toggle() }
                                } label: {
                                    Text(rangeLabel)
                                        .font(Theme.sans(Theme.fontBase))
                                        .foregroundStyle(Theme.text)
                                        .padding(.horizontal, 12)
                                        .frame(height: 32)
                                        .liquidGlass(Capsule(), fill: ActiveFill())
                                }
                                .buttonStyle(.plain)
                            }
                            .transition(.opacity)
                        }
                    }
                    .frame(minHeight: 44)
                    .padding(.top, 40)

                    SegmentedControl(selection: dateModeBinding,
                                     options: FiltersResult.periodOptions + [(value: FiltersResult.customDates, label: "Свои даты")])
                    .padding(.top, 24)
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.bottom, 180)
            }
            .coordinateSpace(name: "filtersScroll")
            .onPreferenceChange(SheetScrollTopKey.self) { scrollAtTop = $0 >= -1 }
            .modifier(NoBounceWhenFits())

            if calendarOpen {
                VStack(spacing: 8) {
                    AppCalendar(start: $dateStart, end: $dateEnd)
                }
                .onChange(of: dateEnd) { end in
                    if dateStart != nil { dateMode = FiltersResult.customDates }
                    if end != nil { withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { calendarOpen = false } }
                }
                .padding(.horizontal, Theme.screenX)
                .padding(.top, 87)
                .transition(.scale(scale: 0.92, anchor: .bottomTrailing).combined(with: .opacity))
            }

            // Закреплённые внизу «Показать N материалов» и «Сбросить» — в зоне большого пальца.
            VStack(spacing: 4) {
                AppButton(title: applyTitle,
                          disabled: countFor != nil && !counting && count == 0,
                          action: apply)
                Button(action: reset) {
                    Text("Сбросить")
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(current.activeCount > 0 ? Theme.text : Theme.textSecondary)
                        .padding(.horizontal, 24)
                        .frame(height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressableStyle())
                .disabled(current.activeCount == 0)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 12)
            .padding(.bottom, 24)
            .background(Theme.surface)
            .padding(.bottom, 16)
            .frame(maxHeight: .infinity, alignment: .bottom)

            Capsule().fill(Color.white).frame(width: 32, height: 1.5)
                .frame(maxWidth: .infinity)
                .padding(.top, 11)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .task(id: current) { await recount(current) }
        .background(Theme.surface, in: UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous))
        .ignoresSafeArea(.container, edges: .bottom)
        .offset(y: max(dragY, 0))
        // Закрытие свайпом вниз, как у системных листов: тянуть можно за любое место,
        // в том числе за список, если он у самого верха. Жест идёт вместе с прокруткой.
        .simultaneousGesture(
            DragGesture(minimumDistance: 8)
                .onChanged { v in
                    if dragsSheet == nil {
                        dragsSheet = scrollAtTop && v.translation.height > abs(v.translation.width)
                    }
                    if dragsSheet == true { dragY = v.translation.height }
                }
                .onEnded { v in
                    defer { dragsSheet = nil }
                    guard dragsSheet == true else { return }
                    if v.predictedEndTranslation.height > 260 || (v.translation.height > 120 && v.predictedEndTranslation.height >= v.translation.height) {
                        // Выбор не теряется: закрытие свайпом применяет его.
                        onClose()
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dragY = 0 }
                    }
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
        onApply(current)
    }

    private func reset() {
        Haptics.light()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
            for i in instruments.indices { instruments[i].on = false }
            for i in statuses.indices { statuses[i].on = false }
            dateMode = nil; dateStart = nil; dateEnd = nil
            calendarOpen = false
        }
    }

    /// «Показать N материалов»: число с сервера с небольшой задержкой после выбора.
    private func recount(_ filters: FiltersResult) async {
        onDraftChange(filters)
        guard let countFor else { return }
        counting = true
        try? await Task.sleep(nanoseconds: 300_000_000)
        guard !Task.isCancelled else { return }
        count = await countFor(filters)
        counting = false
    }
}

/// Затемнение rgba(7,7,7,0.71) с размытием 6.45 и лист фильтров от y=172.
struct FiltersOverlay: View {
    @Binding var isOpen: Bool
    let applied: FiltersResult
    let onApply: (FiltersResult) -> Void
    var countFor: ((FiltersResult) async -> Int?)? = nil

    /// Выбор в листе: закрытие свайпом или тапом по фону применяет его, а не теряет.
    @State private var draft: FiltersResult? = nil

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

                FiltersSheet(initial: applied,
                             onApply: { onApply($0); draft = nil; isOpen = false },
                             onClose: close,
                             countFor: countFor,
                             onDraftChange: { draft = $0 })
                    .padding(.top, 172)
                    .transition(.move(edge: .bottom))
            }
        }
        .ignoresSafeArea(.container, edges: .top)
        // Прототип: «Аналитика» → Chips → оверлей «Frame 287»: MOVE_IN снизу,
        // пружина SLOW (≈1.25 с), затемнение под листом.
        .animation(Motion.slow, value: isOpen)
    }

    private func close() {
        if let draft, draft != applied.normalized { onApply(draft) }
        draft = nil
        isOpen = false
    }
}

/// Верх содержимого листа в его прокрутке.
private struct SheetScrollTopKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// Список, который помещается целиком, не пружинит: протяжка вниз двигает лист, а не список.
private struct NoBounceWhenFits: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.scrollBounceBehavior(.basedOnSize)
        } else {
            content
        }
    }
}
