import SwiftUI

struct AdminsView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var admins: [AdminDto] = []
    @State private var tag = ""
    @State private var error = ""
    @State private var adding = false
    @State private var confirmFor: AdminDto? = nil
    @State private var removing: String? = nil

    private var myTelegramId: String? { session.user?.telegramId }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Администраторы").font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                Text("Админы видят админ-панель: создают и редактируют отчёты, настраивают чаты и отвечают в личных чатах. Добавить нового админа можно по его тегу из профиля, например @ivan.")
                    .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary).lineSpacing(2)

                HStack(spacing: 8) {
                    AppInput(text: $tag, placeholder: "Тег, например @ivan", error: !error.isEmpty, keyboard: .asciiCapable)
                        .onChange(of: tag) { _ in error = "" }
                    Button { add() } label: {
                        Text("Добавить").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                            .padding(.horizontal, 20).frame(height: 48)
                            .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous), fill: ActiveFill())
                    }.buttonStyle(PressableStyle()).disabled(adding)
                }

                if !error.isEmpty { AuthFieldError(text: error) }

                VStack(spacing: 8) {
                    ForEach(admins) { a in adminRow(a) }
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 32).padding(.bottom, 32)
        }
        .screenBackground()
        .task { await load() }
        .alert("Удалить админа?", isPresented: Binding(
            get: { confirmFor != nil },
            set: { if !$0 { confirmFor = nil } }
        )) {
            Button("Удалить", role: .destructive) { if let a = confirmFor { remove(a) } }
            Button("Отмена", role: .cancel) { confirmFor = nil }
        } message: {
            Text("Он потеряет доступ к админ-панели")
        }
    }

    private func adminRow(_ a: AdminDto) -> some View {
        HStack(spacing: 12) {
            Circle().fill(Color(hex: "#d9d9d9")).frame(width: 30, height: 30)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(a.name ?? "Ещё не заходил").font(Theme.sans(Theme.fontSm, .semibold))
                    if a.telegramId == myTelegramId { Text("(вы)").font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary) }
                }
                Text(a.username ?? "ID \(a.telegramId)")
                    .font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            if a.root {
                Text("Главный").font(Theme.sans(13)).tracking(0.52).foregroundStyle(Theme.text)
                    .padding(.horizontal, 12).frame(height: 32).liquidGlass(Capsule(), fill: ActiveFill())
            } else {
                PurplePillButton(title: "Удалить") { confirmFor = a }
                    .disabled(removing == a.telegramId)
            }
        }
        .foregroundStyle(Theme.text)
        .padding(.horizontal, 16).padding(.vertical, 10)
        .frame(minHeight: 64)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func load() async {
        do { admins = try await Api.fetchAdmins() }
        catch let e as ApiError { self.error = e.message }
        catch { self.error = "Не удалось загрузить список" }
    }

    private func add() {
        let value = tag.trimmed
        guard value.range(of: #"^@?[\p{L}\p{N}_.]{1,64}$"#, options: .regularExpression) != nil else { self.error = "Укажите тег пользователя, например @ivan"; return }
        adding = true; error = ""
        Task {
            do { try await Api.addAdmin(tag: value); tag = ""; Haptics.medium(); await load() }
            catch let e as ApiError { self.error = e.fieldError("tag") ?? e.message }
            catch { self.error = "Не удалось добавить" }
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
            } catch let e as ApiError { self.error = e.message; confirmFor = nil }
            catch { self.error = "Не удалось удалить"; confirmFor = nil }
            removing = nil
        }
    }
}
