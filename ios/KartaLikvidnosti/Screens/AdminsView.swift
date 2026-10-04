import SwiftUI

struct AdminsView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var admins: [AdminDto] = []
    @State private var telegramId = ""
    @State private var error = ""
    @State private var adding = false
    @State private var confirmFor: AdminDto? = nil
    @State private var removing: String? = nil

    private var myTelegramId: String? { session.user?.telegramId }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Администраторы").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                Text("Админы видят админ-панель: создают и редактируют отчёты, настраивают чаты и отвечают в личных чатах. Добавить нового админа можно по его Telegram ID.")
                    .font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineSpacing(2)

                HStack(spacing: 8) {
                    AppInput(text: $telegramId, placeholder: "Telegram ID", error: !error.isEmpty, keyboard: .numberPad)
                        .onChange(of: telegramId) { _ in error = "" }
                    Button { add() } label: {
                        Text("Добавить").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                            .padding(.horizontal, 20).frame(height: 48).background(Theme.gradientAccent)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
                    }.buttonStyle(.plain).disabled(adding)
                }

                if !error.isEmpty { Text(error).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }

                VStack(spacing: 8) {
                    ForEach(admins) { a in adminRow(a) }
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 32)
        }
        .screenBackground(Theme.bg)
        .task { await load() }
        .overlay {
            if let a = confirmFor {
                ConfirmDialog(title: "Удалить админа?", text: "Он потеряет доступ к админ-панели",
                              busy: removing != nil, onConfirm: { remove(a) }, onCancel: { confirmFor = nil })
            }
        }
    }

    private func adminRow(_ a: AdminDto) -> some View {
        HStack(spacing: 12) {
            Circle().fill(Color(hex: "#8a8a8a")).frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(a.name ?? "Ещё не заходил").font(.system(size: Theme.fontMd, weight: .bold))
                    if a.telegramId == myTelegramId { Text("(вы)").font(.system(size: Theme.fontMd)).foregroundStyle(Theme.textSecondary) }
                }
                Text("ID \(a.telegramId)" + (a.username.map { " · \($0)" } ?? ""))
                    .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            if a.root {
                Text("Главный").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.text)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(Theme.gradientAccent).clipShape(Capsule())
            } else {
                Button { confirmFor = a } label: {
                    Text("Удалить").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 12).frame(height: 32).background(Theme.gradientPurpleSolid).clipShape(Capsule())
                }.buttonStyle(.plain).disabled(removing == a.telegramId)
            }
        }
        .foregroundStyle(Theme.text)
        .padding(.horizontal, 16).padding(.vertical, 10)
        .frame(minHeight: 64)
        .background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func load() async {
        do { admins = try await Api.fetchAdmins() }
        catch let e as ApiError { error = e.message }
        catch { error = "Не удалось загрузить список" }
    }

    private func add() {
        let id = telegramId.trimmed
        guard id.range(of: #"^\d+$"#, options: .regularExpression) != nil else { error = "Telegram ID состоит только из цифр"; return }
        adding = true; error = ""
        Task {
            do { try await Api.addAdmin(telegramId: id); telegramId = ""; Haptics.medium(); await load() }
            catch let e as ApiError { error = e.fieldError("telegram_id") ?? e.message }
            catch { error = "Не удалось добавить" }
            adding = false
        }
    }

    private func remove(_ a: AdminDto) {
        removing = a.telegramId
        Task {
            do {
                try await Api.removeAdmin(telegramId: a.telegramId)
                Haptics.medium(); confirmFor = nil
                if a.telegramId == myTelegramId {
                    await session.load(force: true)
                    router.popToRoot()
                    router.switchTab(.profile)
                } else { await load() }
            } catch let e as ApiError { error = e.message; confirmFor = nil }
            catch { error = "Не удалось удалить"; confirmFor = nil }
            removing = nil
        }
    }
}
