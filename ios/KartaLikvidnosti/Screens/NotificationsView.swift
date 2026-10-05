import SwiftUI

struct NotificationsView: View {
    @State private var allNotifications = true
    @State private var byInstrument = false
    @State private var instruments: [InstrumentDto] = []

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                Text("Уведомления").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                VStack(spacing: 18) {
                    HStack {
                        Text("Все уведомления").font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
                        Spacer()
                        AppToggle(isOn: $allNotifications)
                    }
                    if !allNotifications {
                        HStack {
                            Text("Только по инструментам").font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
                            Spacer()
                            AppToggle(isOn: $byInstrument)
                        }
                    }
                }
                .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

                Text("Уведомления\nпо инструментам").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                Text("Для этой функции выше включите уведомления по инструментам")
                    .font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textMuted)

                FlowLayout(spacing: 8) {
                    ForEach(instruments.indices, id: \.self) { i in
                        InstrumentChip(label: instruments[i].label, on: Binding(
                            get: { instruments[i].on },
                            set: { instruments[i].on = $0; saveInstruments() }))
                    }
                }
                .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 40)
        }
        .screenBackground(Theme.bg)
        .task { await load() }
        .onChange(of: allNotifications) { v in Task { try? await Api.updateNotifications(["all": v]) } }
        .onChange(of: byInstrument) { v in Task { try? await Api.updateNotifications(["by_instrument": v]) } }
    }

    private func load() async {
        do {
            let p = try await Api.fetchProfile()
            allNotifications = p.notifications?.all ?? true
            byInstrument = p.notifications?.byInstrument ?? false
            instruments = p.instruments
        } catch {}
    }

    private func saveInstruments() {
        Task { try? await Api.syncInstruments(instruments.filter { $0.on }.map { $0.key }) }
    }
}
