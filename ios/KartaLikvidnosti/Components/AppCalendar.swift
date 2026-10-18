import SwiftUI

/// Календарь выбора диапазона дат (ui/AppCalendar.vue).
/// Поддерживает выбор начальной и конечной даты: первый тап задаёт начало,
/// следующий — конец (если он позже начала), иначе диапазон начинается заново.
struct AppCalendar: View {
    @Binding var start: String?   // ISO yyyy-MM-dd
    @Binding var end: String?     // ISO yyyy-MM-dd
    var today: String = "2026-03-28"

    @State private var viewYear: Int
    @State private var viewMonth: Int  // 0..11

    private static let months = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                                 "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"]
    private static let weekdays = ["ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"]

    init(start: Binding<String?>, end: Binding<String?>, today: String = "2026-03-28") {
        _start = start
        _end = end
        self.today = today
        let base = start.wrappedValue ?? today
        let comps = Self.parse(base) ?? (2026, 3, 1)
        _viewYear = State(initialValue: comps.0)
        _viewMonth = State(initialValue: comps.1 - 1)
    }

    private struct Cell: Identifiable { let id = UUID(); let day: Int; let iso: String?; let out: Bool }

    // Calendar из макета: стеклянная плашка 361×385 («черный 2» + glass),
    // шапка с шевронами и месяцем (Min Sans SemiBold 20), линия 40%, дни недели
    // Medium 12 капсом, даты Medium 18 в ячейках 37×42; начало диапазона —
    // зелёная капсула, конец — белая с зелёной цифрой; чужие дни — 40%.
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 9) {
                Button { prevMonth() } label: { Icon(AppIcons.monthPrev, size: 24, color: Theme.text) }
                    .buttonStyle(.plain)
                Text(Self.months[viewMonth])
                    .font(Theme.sans(20, .semibold))
                    .foregroundStyle(Theme.text)
                    .frame(maxWidth: .infinity)
                Button { nextMonth() } label: { Icon(AppIcons.monthNext, size: 24, color: Theme.text) }
                    .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .frame(height: 51)
            .padding(.top, 4)

            Rectangle().fill(Color.white.opacity(0.4)).frame(height: 1)
                .padding(.horizontal, 3)
                .padding(.top, 8)

            HStack(spacing: 0) {
                ForEach(Self.weekdays, id: \.self) { w in
                    Text(w).font(Theme.sans(Theme.fontSm, .medium)).tracking(0.36)
                        .foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 16)
            .padding(.top, 20)
            .padding(.bottom, 16)

            VStack(spacing: 0) {
                ForEach(0..<(cells.count / 7), id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(cells[(row * 7)..<(row * 7 + 7)]) { cell in
                            Button { select(cell) } label: { dayView(cell) }
                                .buttonStyle(.plain)
                                .disabled(cell.out)
                        }
                    }
                    .frame(height: 50)
                }
            }
            .padding(.bottom, 4)
        }
        .padding(.horizontal, 14.83)
        .frame(width: 361)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous), fill: Theme.black2)
    }

    @ViewBuilder private func dayView(_ cell: Cell) -> some View {
        let isStart = cell.iso != nil && cell.iso == start
        let isEnd = cell.iso != nil && cell.iso == end && end != start
        let inRange = isInRange(cell.iso)
        ZStack {
            if inRange {
                Capsule().fill(Theme.green.opacity(0.3)).frame(width: 37, height: 42)
            }
            if isStart {
                Capsule().fill(Theme.green).frame(width: 37, height: 42)
            } else if isEnd {
                Capsule().fill(Color.white).frame(width: 37, height: 42)
            }
            Text("\(cell.day)")
                .font(Theme.sans(18, .medium))
                .foregroundStyle(isEnd ? Theme.green : Theme.text)
                .opacity(cell.out ? 0.4 : 1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 50)
        .contentShape(Rectangle())
    }

    /// Дата строго между выбранными краями (для подсветки «моста»).
    private func isInRange(_ iso: String?) -> Bool {
        guard let iso = iso, let s = start, let e = end else { return false }
        return iso > s && iso < e
    }

    private var cells: [Cell] {
        var result: [Cell] = []
        let first = DateComponents(calendar: .current, year: viewYear, month: viewMonth + 1, day: 1).date ?? Date()
        let weekday = Calendar.current.component(.weekday, from: first) // 1=Sun..7=Sat
        let lead = (weekday + 5) % 7 // понедельник = 0
        let daysInMonth = Calendar.current.range(of: .day, in: .month, for: first)?.count ?? 30
        let prevMonthDate = DateComponents(calendar: .current, year: viewYear, month: viewMonth, day: 1).date ?? Date()
        let daysPrev = Calendar.current.range(of: .day, in: .month, for: prevMonthDate)?.count ?? 30
        for i in 0..<lead {
            result.append(Cell(day: daysPrev - lead + 1 + i, iso: nil, out: true))
        }
        for d in 1...daysInMonth {
            let iso = String(format: "%04d-%02d-%02d", viewYear, viewMonth + 1, d)
            result.append(Cell(day: d, iso: iso, out: false))
        }
        var tail = 1
        while result.count % 7 != 0 {
            result.append(Cell(day: tail, iso: nil, out: true)); tail += 1
        }
        return result
    }

    private func prevMonth() {
        if viewMonth == 0 { viewMonth = 11; viewYear -= 1 } else { viewMonth -= 1 }
    }
    private func nextMonth() {
        if viewMonth == 11 { viewMonth = 0; viewYear += 1 } else { viewMonth += 1 }
    }

    /// Логика диапазона: нет начала или уже выбраны обе даты → начинаем заново.
    /// Иначе второй тап задаёт конец (если позже начала) либо переносит начало.
    private func select(_ cell: Cell) {
        guard !cell.out, let iso = cell.iso else { return }
        if start == nil || end != nil {
            start = iso; end = nil
        } else if let s = start {
            if iso >= s { end = iso } else { start = iso }
        }
    }

    private static func parse(_ iso: String) -> (Int, Int, Int)? {
        let parts = iso.split(separator: "-")
        guard parts.count == 3, let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]) else { return nil }
        return (y, m, d)
    }
}
