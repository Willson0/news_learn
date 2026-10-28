import SwiftUI

/// «Управление подпиской» (Figma 337:26959, привязка 337:26981): заголовок
/// на y=160; блок «Подписка / Тарифный план» (Bold 16, статус SemiBold 16
/// зелёный или красный, «до …» 14 pt белый 65 %, линия 10 %); через 12 —
/// блок счёта 64 pt (белый круг 32 + «СБП 1488» 16 pt, пурпурная пилюля
/// «Отвязать»; без счёта — круг с «+» и «Новый счет», «Привязать»); блок
/// «Авто-платеж» со свитчем.
struct SubscriptionView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var appData: AppData
    /// Состояние подписки известно (из кэша или с сервера). До этого вместо статуса —
    /// мигающие блоки: экран не «доезжает» из «Неактивна» и выключенного автоплатежа.
    @State private var known = false
    @State private var active = false
    @State private var until = ""
    @State private var price = ""
    @State private var cardInfo: CardDto? = nil
    @State private var cardKnown = false
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
                            if !known {
                                SkeletonBlock(width: 90, height: 18, radius: 6)
                            } else {
                            Text(active ? "Активна" : "Неактивна")
                                .font(Theme.sans(Theme.fontMd, .semibold))
                                .foregroundStyle(active ? Theme.green : Theme.error)
                            }
                            if known, active, !until.isEmpty {
                                Text("до \(until)").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                            }
                        }
                    }
                    Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                    HStack {
                        Text("Тарифный план").font(Theme.sans(Theme.fontMd, .bold)).foregroundStyle(Theme.text)
                        Spacer(minLength: 8)
                        if known {
                            Text(price.isEmpty ? "—" : price).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        } else {
                            SkeletonBlock(width: 110, height: 16, radius: 6)
                        }
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
                    if cardKnown {
                        Text(cardTitle).font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text)
                            .lineLimit(1)
                    } else {
                        SkeletonBlock(width: 120, height: 16, radius: 6)
                    }
                    Spacer(minLength: 8)
                    if !cardKnown {
                        SkeletonBlock(width: 96, height: 36, radius: 18)
                    } else if cardBusy {
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
                    // Переключение пользователем сразу уходит на сервер; значение с сервера
                    // ставится без анимации и без повторной отправки.
                    AppToggle(isOn: Binding(get: { autoPay }, set: { setAutoPay($0) }), disabled: !known)
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
        .onAppear {
            withoutAnimation {
                if !known, let s = appData.subscription { apply(s) }
                if !cardKnown, let c = appData.card { cardInfo = c; cardKnown = true }
            }
        }
        .task {
            await load()
            await reloadCard()
        }
    }

    private func apply(_ s: SubscriptionDto) {
        active = s.active; until = s.until ?? ""; price = s.price ?? ""
        autoPay = s.autoPay
        known = true
    }

    private func setAutoPay(_ value: Bool) {
        autoPay = value
        Task {
            do {
                try await Api.updateSubscription(["auto_pay": value])
                await load()
            } catch {
                withoutAnimation { autoPay = !value }
            }
        }
    }

    private func load() async {
        do {
            if let s = try await Api.fetchSubscription() {
                withoutAnimation { apply(s) }
                appData.subscription = s
                session.setSubscription(s.active)
            }
        } catch {}
        if !known { withoutAnimation { known = true } }
    }

    private var isBound: Bool { cardInfo?.bound == true }

    private var cardTitle: String {
        guard isBound, let info = cardInfo else { return "Новая карта" }
        let last = info.last4.map { " •••• \($0)" } ?? ""
        return (info.brand ?? "Карта") + last
    }

    private func reloadCard() async {
        let info = try? await Api.fetchCard()
        withoutAnimation {
            if let info { cardInfo = info; appData.card = info }
            cardKnown = true
        }
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
