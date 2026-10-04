import SwiftUI

struct SubscriptionView: View {
    @State private var active = false
    @State private var until = ""
    @State private var plan = ""
    @State private var account: String? = nil
    @State private var autoPay = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Управление подпиской").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

            VStack(spacing: 0) {
                HStack {
                    Text("Подписка").font(.system(size: Theme.fontMd, weight: .semibold)).foregroundStyle(Theme.text)
                    Spacer()
                    HStack(spacing: 8) {
                        Text(active ? "Активна" : "Неактивна")
                            .foregroundStyle(active ? Color(hex: "#7bc043") : Theme.error)
                            .font(.system(size: Theme.fontBase, weight: .semibold))
                        if active { Text("до \(until)").foregroundStyle(Theme.textSecondary).font(.system(size: Theme.fontBase)) }
                    }
                }.padding(.vertical, 4)
                Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1).padding(.vertical, 10)
                HStack {
                    Text("Тарифный план").font(.system(size: Theme.fontMd, weight: .semibold)).foregroundStyle(Theme.text)
                    Spacer()
                    Text(plan).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                }.padding(.vertical, 4)
            }
            .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color.white).frame(width: 36, height: 36)
                    if account == nil { Icon(AppIcons.plus, size: 20, color: Color(hex: "#1a1b1d")) }
                }
                Text(account ?? "Новый счет").font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
                Spacer()
                Button { toggleAccount() } label: {
                    Text(account == nil ? "Привязать" : "Отвязать").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 18).padding(.vertical, 9)
                        .background(Theme.gradientPurpleSolid).clipShape(Capsule())
                }.buttonStyle(.plain)
            }
            .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

            HStack {
                Text("Авто-платеж").font(.system(size: Theme.fontMd, weight: .semibold)).foregroundStyle(Theme.text)
                Spacer()
                AppToggle(isOn: $autoPay)
            }
            .padding(16).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

            Spacer()
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 40)
        .screenBackground(Theme.bg)
        .task { await load() }
        .onChange(of: autoPay) { v in Task { try? await Api.updateSubscription(["auto_pay": v]) } }
    }

    private func load() async {
        do {
            if let s = try await Api.fetchSubscription() {
                active = s.active; until = s.until ?? ""; plan = s.plan ?? ""
                autoPay = s.autoPay; account = s.paymentMethod
            }
        } catch {}
    }

    private func toggleAccount() {
        account = account == nil ? "СБП 1488" : nil
        let method = account
        Task { try? await Api.updateSubscription(["payment_method": method]) }
    }
}
