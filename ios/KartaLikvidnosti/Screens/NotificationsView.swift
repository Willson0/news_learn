import SwiftUI

/// «Профиль push-уведомления» (Figma 337:26945, 337:27005, 349:8338):
/// заголовок Kreadon 24 на y=160, блок свитчей 361 (#25262A, радиус 24,
/// строки 43, Min Sans 14), через 40 — «Уведомления по инструментам»,
/// подпись 12 pt белый 65 %, блок чипов (отступ 12, шаг 4).
/// «Только по инструментам» показывается, пока не включены все уведомления.
struct NotificationsView: View {
    @EnvironmentObject var appData: AppData
    @State private var allNotifications = true
    @State private var byInstrument = false
    @State private var instruments: [InstrumentDto] = []

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Уведомления").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                VStack(spacing: 0) {
                    switchRow("Все уведомления", Binding(get: { allNotifications }, set: { v in
                        allNotifications = v
                        Task { try? await Api.updateNotifications(["all": v]) }
                    }))
                    if !allNotifications {
                        switchRow("Только по инструментам", Binding(get: { byInstrument }, set: { v in
                            byInstrument = v
                            Task { try? await Api.updateNotifications(["by_instrument": v]) }
                        }))
                            .transition(.opacity)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                .padding(.top, 24)

                Text("Уведомления\nпо инструментам").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    .padding(.top, 40)
                Text("Для этой функции выше включите\nуведомления по инструментам")
                    .font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    .padding(.top, 12)

                FlowLayout(spacing: 4, lineSpacing: 4) {
                    ForEach(instruments.indices, id: \.self) { i in
                        InstrumentChip(label: instruments[i].label, on: Binding(
                            get: { instruments[i].on },
                            set: { instruments[i].on = $0; saveInstruments() }))
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                .padding(.top, 24)
                .opacity(byInstrument || allNotifications ? 1 : 0.5)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 32)
            .padding(.bottom, 40)
        }
        .screenBackground()
        // Прототип: «Профиль push-уведомления» 484:21266 ↔ 484:21302 — блок схлопывается,
        // ниже всё подтягивается: SMART_ANIMATE 800 мс, ease-in-out-back.
        .animation(Motion.standard, value: allNotifications)
        .animation(Motion.standard, value: byInstrument)
        // Экран сразу открывается с известными настройками (кэш профиля), без «доезжания»
        // переключателей; ответ сервера ставится без анимации.
        .onAppear { if let p = appData.profile, instruments.isEmpty { withoutAnimation { apply(p) } } }
        .task { await load() }
    }

    private func apply(_ p: ProfileResponse) {
        allNotifications = p.notifications?.all ?? true
        byInstrument = p.notifications?.byInstrument ?? false
        instruments = p.instruments
    }

    private func switchRow(_ title: String, _ isOn: Binding<Bool>) -> some View {
        HStack {
            Text(title).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
            Spacer(minLength: 8)
            AppToggle(isOn: isOn)
        }
        .frame(height: 43)
    }

    private func load() async {
        do {
            let p = try await Api.fetchProfile()
            withoutAnimation { apply(p) }
            appData.profile = p
        } catch {}
    }

    private func saveInstruments() {
        Task { try? await Api.syncInstruments(instruments.filter { $0.on }.map { $0.key }) }
    }
}
