import SwiftUI

/// «Управление подпиской» (Figma 337:26959, привязка 337:26981): заголовок
/// на y=160; блок «Подписка / Тарифный план» (Bold 16, статус SemiBold 16
/// зелёный или красный, «до …» 14 pt белый 65 %, линия 10 %); через 12 —
/// блок счёта 64 pt (белый круг 32 + «СБП 1488» 16 pt, пурпурная пилюля
/// «Отвязать»; без счёта — круг с «+» и «Новый счет», «Привязать»); блок
/// «Авто-платеж» со свитчем.
struct SubscriptionView: View {
    @State private var active = false
    @State private var until = ""
    @State private var plan = ""
    @State private var account: String? = nil
    @State private var autoPay = false

    private let card = RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous)

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Управление подпиской").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)

                VStack(spacing: 12) {
                    HStack(alignment: .top) {
                        Text("Подписка").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                        Spacer(minLength: 8)
                        VStack(alignment: .trailing, spacing: 4) {
                            Text(active ? "Активна" : "Неактивна")
                                .font(Theme.sans(Theme.fontMd, .semibold))
                                .foregroundStyle(active ? Theme.green : Theme.error)
                            if active, !until.isEmpty {
                                Text("до \(until)").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                            }
                        }
                    }
                    Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                    HStack {
                        Text("Тарифный план").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                        Spacer(minLength: 8)
                        Text(plan.isEmpty ? "—" : plan).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                }
                .padding(16)
                .background(Theme.surface, in: card)
                .padding(.top, 24)

                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(Color.white).frame(width: 32, height: 32)
                        if account == nil { Icon(AppIcons.plus, size: 20, color: Theme.green) }
                    }
                    Text(account ?? "Новый счет").font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text)
                    Spacer(minLength: 8)
                    PurplePillButton(title: account == nil ? "Привязать" : "Отвязать") { toggleAccount() }
                }
                .padding(.horizontal, 16)
                .frame(height: 64)
                .background(Theme.surface, in: card)
                .padding(.top, 12)

                HStack {
                    Text("Авто-платеж").font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text)
                    Spacer(minLength: 8)
                    AppToggle(isOn: $autoPay)
                }
                .padding(.horizontal, 16)
                .frame(height: 64)
                .background(Theme.surface, in: card)
                .padding(.top, 12)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 32)
            .padding(.bottom, 40)
        }
        .screenBackground()
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
        Haptics.light()
        account = account == nil ? "СБП 1488" : nil
        let method = account
        Task { try? await Api.updateSubscription(["payment_method": method]) }
    }
}
