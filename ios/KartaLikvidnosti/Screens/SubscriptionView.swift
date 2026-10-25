import SwiftUI

/// «Управление подпиской» (Figma 337:26959, привязка 337:26981): заголовок
/// на y=160; блок «Подписка / Тарифный план» (Bold 16, статус SemiBold 16
/// зелёный или красный, «до …» 14 pt белый 65 %, линия 10 %); через 12 —
/// блок счёта 64 pt (белый круг 32 + «СБП 1488» 16 pt, пурпурная пилюля
/// «Отвязать»; без счёта — круг с «+» и «Новый счет», «Привязать»); блок
/// «Авто-платеж» со свитчем.
struct SubscriptionView: View {
    @EnvironmentObject var session: Session
    @State private var active = false
    @State private var until = ""
    @State private var price = ""
    @State private var cardInfo: CardDto? = nil
    @State private var cardBusy = false
    @State private var cardError: String? = nil
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
                        Text(price.isEmpty ? "—" : price).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                    }
                }
                .padding(16)
                .background(Theme.surface, in: card)
                .padding(.top, 24)

                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(Color.white).frame(width: 32, height: 32)
                        if !isBound { Icon(AppIcons.plus, size: 20, color: Theme.green) }
                    }
                    Text(cardTitle).font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    if cardBusy {
                        ProgressView().frame(width: 96, height: 36)
                    } else {
                        PurplePillButton(title: isBound ? "Отвязать" : "Привязать") { isBound ? unbindCard() : bindCard() }
                    }
                }
                .padding(.horizontal, 16)
                .frame(height: 64)
                .background(Theme.surface, in: self.card)
                .padding(.top, 12)
                if let cardError {
                    Text(cardError).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.error)
                        .padding(.top, 8)
                }

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
        .task {
            await load()
            await reloadCard()
        }
        .onChange(of: autoPay) { v in Task { try? await Api.updateSubscription(["auto_pay": v]) } }
    }

    private func load() async {
        do {
            if let s = try await Api.fetchSubscription() {
                active = s.active; until = s.until ?? ""; price = s.price ?? ""
                autoPay = s.autoPay
                session.setSubscription(s.active)
            }
        } catch {}
    }

    private var isBound: Bool { cardInfo?.bound == true }

    private var cardTitle: String {
        guard isBound, let info = cardInfo else { return "Новая карта" }
        let last = info.last4.map { " •••• \($0)" } ?? ""
        return (info.brand ?? "Карта") + last
    }

    private func reloadCard() async {
        cardInfo = try? await Api.fetchCard()
    }

    private func bindCard() {
        Haptics.light()
        cardError = nil
        Task { @MainActor in
            cardBusy = true
            defer { cardBusy = false }
            do {
                try await CardBinding.run()
                // Уведомление Т-Кассы сохраняет карту чуть позже возврата в приложение, поэтому опрашиваем.
                for _ in 0..<5 {
                    await reloadCard()
                    if isBound {
                        // Карта привязана: сразу списываем подписку (если её ещё нет), обновляем статус.
                        await load()
                        return
                    }
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                }
                cardError = "Карта ещё обрабатывается, обновите экран через минуту"
            } catch {
                cardError = (error as? ApiError)?.message ?? "Не удалось привязать карту"
            }
        }
    }

    private func unbindCard() {
        Haptics.light()
        cardError = nil
        Task { @MainActor in
            cardBusy = true
            defer { cardBusy = false }
            do {
                try await Api.unbindCard()
                await reloadCard()
            } catch {
                cardError = (error as? ApiError)?.message ?? "Не удалось отвязать карту"
            }
        }
    }
}
