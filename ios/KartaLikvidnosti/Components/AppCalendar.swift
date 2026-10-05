import SwiftUI

/// Календарь выбора даты (ui/AppCalendar.vue).
struct AppCalendar: View {
    @Binding var selected: String      // ISO yyyy-MM-dd
    var today: String = "2026-03-28"

    @State private var viewYear: Int
    @State private var viewMonth: Int  // 0..11

    private static let months = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                                 "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"]
    private static let weekdays = ["ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"]

    init(selected: Binding<String>, today: String = "2026-03-28") {
        _selected = selected
        self.today = today
        let comps = Self.parse(selected.wrappedValue) ?? (2026, 3, 1)
        _viewYear = State(initialValue: comps.0)
        _viewMonth = State(initialValue: comps.1 - 1)
    }

    private struct Cell: Identifiable { let id = UUID(); let day: Int; let iso: String?; let out: Bool }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { prevMonth() } label: { Icon(AppIcons.back, size: 20, color: Theme.text) }
                Spacer()
                Text(Self.months[viewMonth]).font(.system(size: Theme.fontMd, weight: .bold))
                    .foregroundStyle(Theme.text)
                Spacer()
                Button { nextMonth() } label: { Icon(AppIcons.chevronRight, size: 20, color: Theme.text) }
            }
            .padding(.bottom, 12)

            Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1).padding(.bottom, 8)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7), spacing: 2) {
                ForEach(Self.weekdays, id: \.self) { w in
                    Text(w).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                ForEach(cells) { cell in
                    Button { select(cell) } label: { dayView(cell) }
                        .buttonStyle(.plain)
                        .disabled(cell.out)
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    @ViewBuilder private func dayView(_ cell: Cell) -> some View {
        let isSelected = cell.iso == selected
        let isToday = cell.iso == today && cell.iso != selected
        ZStack {
            Circle()
                .fill(isSelected ? Theme.green : (isToday ? Color.white : Color.clear))
                .frame(width: 34, height: 34)
            Text("\(cell.day)")
                .font(.system(size: Theme.fontBase))
                .foregroundStyle(cell.out ? Theme.textMuted : (isToday ? Theme.green : Theme.text))
        }
        .frame(height: 40)
        .frame(maxWidth: .infinity)
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
    private func select(_ cell: Cell) {
        guard !cell.out, let iso = cell.iso else { return }
        selected = iso
    }

    private static func parse(_ iso: String) -> (Int, Int, Int)? {
        let parts = iso.split(separator: "-")
        guard parts.count == 3, let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]) else { return nil }
        return (y, m, d)
    }
}
