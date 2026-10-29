import SwiftUI

/// «Управление подпиской» (Figma 337:26959, привязка 337:26981): заголовок
/// на y=160; блок «Подписка / Тарифный план» (Bold 16, статус SemiBold 16
/// зелёный или красный, «до …» 14 pt белый 65 %, линия 10 %); через 12 —
/// блок счёта 64 pt (белый круг 32 + «СБП 1488» 16 pt, пурпурная пилюля
/// «Отвязать»; без счёта — круг с «+» и «Новый счет», «Привязать»); блок
/// «Автопродление» со свитчем и строкой «Следующее списание …».
/// После возврата из Т-Кассы — полноэкранное «Проверяем платёж…» и итог.
struct SubscriptionView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var appData: AppData
    @EnvironmentObject var router: Router
    @Environment(\.scenePhase) private var scenePhase

    enum PaymentCheck: Equatable {
        case checking
        case active(until: String)
        case pending(String)
        case failed(String)
    }
    @State private var paymentCheck: PaymentCheck? = nil
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
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Автопродление").font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text)
                        // Когда и сколько спишется — видно до того, как это случится.
                        if known, autoPay, active, !until.isEmpty {
                            Text("Следующее списание \(until)" + (price.isEmpty ? "" : ", \(price)"))
                                .font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Spacer(minLength: 8)
                    // Переключение пользователем сразу уходит на сервер; значение с сервера
                    // ставится без анимации и без повторной отправки.
                    AppToggle(isOn: Binding(get: { autoPay }, set: { setAutoPay($0) }), disabled: !known)
                        .accessibilityLabel("Автопродление")
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 64)
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
        // Вернулись в приложение (например, из браузера банка) — статус мог измениться.
        .onChange(of: scenePhase) { phase in
            if phase == .active, paymentCheck == nil { Task { await load() } }
        }
        .overlay {
            if let paymentCheck {
                PaymentCheckView(check: paymentCheck,
                                 onToMaterials: { self.paymentCheck = nil; router.switchTab(.home) },
                                 onRetry: { self.paymentCheck = nil; bindCard() },
                                 onClose: { self.paymentCheck = nil })
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: paymentCheck)
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
                AppToast.shared.show("Не удалось сохранить, попробуйте ещё раз")
            }
        }
    }

    private func load() async {
        do {
            if let s = try await Api.fetchSubscription() {
                withoutAnimation { apply(s) }
                appData.subscription = s
                session.setSubscription(s)
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
                await checkPayment()
            } catch let err as ApiError where err.status == CardBinding.declinedStatus {
                paymentCheck = .failed("Платёж не прошёл: его отменили или банк отклонил карту. Деньги не списаны.")
                await reloadCard()
            } catch {
                cardError = (error as? ApiError)?.message ?? "Не удалось привязать карту"
            }
        }
    }

    /// Списание приходит асинхронно (уведомлением банка): «Проверяем платёж…» и опрос
    /// раз в 2,5 с до 30 с. Успех — «Подписка активна до …», иначе — понятный текст.
    private func checkPayment() async {
        let wasActive = active
        paymentCheck = .checking
        var bound = false
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            // Запрос карты сам дожидается результата привязки и списывает подписку.
            await reloadCard()
            bound = isBound
            let sub = try? await Api.fetchSubscription()
            if let sub {
                withoutAnimation { apply(sub) }
                appData.subscription = sub
                session.setSubscription(sub)
            }
            if let sub, sub.active, !wasActive || bound {
                paymentCheck = wasActive ? nil : .active(until: sub.until ?? "")
                return
            }
            try? await Task.sleep(nanoseconds: 2_500_000_000)
        }
        paymentCheck = .pending(bound
            ? "Карта привязана. Подписка включится, как только пройдёт списание или администратор назначит тариф — мы пришлём уведомление."
            : "Платёж ещё обрабатывается. Мы пришлём уведомление, как только он пройдёт.")
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

/// Полноэкранное состояние оплаты: проверка, успех, «ещё обрабатывается» или ошибка.
private struct PaymentCheckView: View {
    let check: SubscriptionView.PaymentCheck
    let onToMaterials: () -> Void
    let onRetry: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            switch check {
            case .checking:
                ProgressView().tint(Theme.green).scaleEffect(1.4).frame(height: 40)
                title("Проверяем платёж…")
                note("Банк подтверждает оплату, обычно это несколько секунд")
            case .active(let until):
                title(until.isEmpty ? "Подписка активна" : "Подписка активна до \(until)")
                note("Все материалы уже открыты")
                AppButton(title: "К материалам", variant: .accent) { onToMaterials() }.padding(.top, 8)
            case .pending(let text):
                title("Платёж обрабатывается")
                note(text)
                AppButton(title: "Понятно", variant: .accent) { onClose() }.padding(.top, 8)
            case .failed(let text):
                title("Оплата не прошла")
                note(text)
                AppButton(title: "Попробовать снова", variant: .accent) { onRetry() }.padding(.top, 8)
                AppButton(title: "Закрыть", variant: .secondary) { onClose() }
            }
        }
        .padding(.horizontal, Theme.screenX)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.bg.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture {}
        .accessibilityElement(children: .contain)
        .onAppear { announce() }
        .onChange(of: check) { _ in announce() }
    }

    private func announce() {
        let text: String
        switch check {
        case .checking: text = "Проверяем платёж"
        case .active(let until): text = until.isEmpty ? "Подписка активна" : "Подписка активна до \(until)"
        case .pending: text = "Платёж обрабатывается"
        case .failed: text = "Оплата не прошла"
        }
        UIAccessibility.post(notification: .screenChanged, argument: text)
    }

    private func title(_ t: String) -> some View {
        Text(t).font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text).multilineTextAlignment(.center)
    }

    private func note(_ t: String) -> some View {
        Text(t).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
    }
}
