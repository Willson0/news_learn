import SwiftUI
import PhotosUI

/// «Регистрация», шаг 2 (Figma 337:26682, ошибка 349:9071, приветствие
/// 337:26694): аватар 148 (#D9D9D9) на y=94, «Изменить фотографию» 14 pt,
/// поля «Имя» и «Тег» с подписью (64 pt), внизу правовой текст 14 pt белый
/// 40 % со ссылкой белым и «Зарегистрироваться». После сохранения —
/// «Приветствуем, <имя>!» и вход в приложение.
struct RegisterProfileView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var name = ""
    @State private var tag = ""
    @State private var avatarUrl: String? = nil
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var uploading = false
    @State private var saving = false
    @State private var errors: [String: String] = [:]
    @State private var greeting: String? = nil

    var body: some View {
        if let greeting {
            AuthMessageView(text: "Приветствуем, \(greeting)!")
                .task {
                    try? await Task.sleep(nanoseconds: 1_600_000_000)
                    router.enterApp()
                }
        } else {
            form
        }
    }

    private var form: some View {
        AuthScaffold(showBack: false, contentTop: 94) {
            VStack(spacing: 0) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    VStack(spacing: 12) {
                        ZStack {
                            Circle().fill(Color(hex: "#d9d9d9"))
                            if let url = fileURL(avatarUrl) {
                                RemoteImage(url: url).clipShape(Circle())
                            }
                            if uploading {
                                Circle().fill(Color.black.opacity(0.35))
                                ProgressView().tint(.white)
                            }
                        }
                        .frame(width: 148, height: 148)
                        Text("Изменить фотографию")
                            .font(Theme.sans(Theme.fontBase))
                            .foregroundStyle(Theme.text)
                    }
                }
                .buttonStyle(.plain)
                .disabled(uploading)

                VStack(spacing: 12) {
                    AuthField(error: errors["name"]) {
                        AppInput(text: $name, placeholder: "Введите имя", label: "Имя",
                                 error: errors["name"] != nil, textContentType: .givenName)
                    }
                    AuthField(error: errors["username"]) {
                        AppInput(text: $tag, placeholder: "Придумайте тег", label: "Тег",
                                 error: errors["username"] != nil, textContentType: .username)
                            .onChange(of: tag) { _ in errors["username"] = nil }
                    }
                }
                .padding(.top, 24)
            }
        } buttons: {
            legal
            AppButton(title: "Зарегистрироваться", variant: .accent, loading: saving || uploading) { submit() }
        }
        .onChange(of: photoItem) { _ in loadPicked() }
        .animation(.easeOut(duration: 0.2), value: errors)
        .onAppear {
            name = session.user?.name ?? name
        }
    }

    private var legal: some View {
        (Text("Нажимая на кнопку «Зарегистрироваться», Вы соглашаетесь с ")
            .foregroundColor(Color.white.opacity(0.4))
         + Text("Правовой документацией").foregroundColor(Theme.text))
            .font(Theme.sans(Theme.fontBase))
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func loadPicked() {
        guard let item = photoItem else { return }
        uploading = true
        Task { @MainActor in
            defer { photoItem = nil; uploading = false }
            if let data = try? await item.loadTransferable(type: Data.self),
               let user = try? await Api.uploadAvatar(data: data, filename: "avatar.jpg", mime: "image/jpeg") {
                avatarUrl = user.avatarUrl
            }
        }
    }

    private func submit() {
        let n = name.trimmed
        let t = tag.trimmed.replacingOccurrences(of: "@", with: "")
        var e: [String: String] = [:]
        if n.isEmpty { e["name"] = "Введите имя" }
        if t.isEmpty { e["username"] = "Придумайте тег" }
        errors = e
        guard e.isEmpty else { Haptics.rigid(); return }
        saving = true
        Task { @MainActor in
            do {
                try await Api.updateProfile(name: n, username: t)
                await session.load(force: true)
                Haptics.medium()
                greeting = n
            } catch let err as ApiError {
                Haptics.rigid()
                if let m = err.errors["username"]?.first {
                    errors = ["username": m.lowercased().contains("taken") || m.contains("занят") ? "Тег уже занят" : m]
                } else if let m = err.errors["name"]?.first {
                    errors = ["name": m]
                } else {
                    errors = ["username": err.message]
                }
            } catch {
                errors = ["username": "Не удалось сохранить"]
            }
            saving = false
        }
    }
}
