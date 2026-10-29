import SwiftUI
import PhotosUI

struct ProfileEditView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var name = ""
    @State private var tag = ""
    @State private var avatarUrl: String? = nil
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var uploading = false
    @State private var saving = false

    private var avatarInitial: String {
        let base = tag.replacingOccurrences(of: "@", with: "")
        let src = base.isEmpty ? name : base
        return src.isEmpty ? "" : String(src.prefix(1)).uppercased()
    }

    @State private var tagError: String? = nil

    // «Профиль изменить» (Figma 337:26931, ошибка 349:12444): «назад» и
    // «Готово» (стекло 72×42) на y=78, между ними аватар 148 с y=78,
    // «Изменить фотографию» 14 pt на y=238, поля «Имя» и «Тег» с подписью
    // (64 pt, через 12) с y=281.
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    VStack(spacing: 12) {
                        ZStack {
                            AvatarCircle(url: fileURL(avatarUrl), size: 148, initial: avatarInitial)
                            if uploading {
                                Circle().fill(Color.black.opacity(0.35)).frame(width: 148, height: 148)
                                ProgressView().tint(.white)
                            }
                        }
                        Text(uploading ? "Загрузка…" : "Изменить фотографию")
                            .font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                    }
                }
                .buttonStyle(.plain)
                .disabled(uploading)
                .contextMenu {
                    if avatarUrl != nil {
                        Button("Удалить фото", role: .destructive) { removePhoto() }
                    }
                }

                VStack(spacing: 12) {
                    AppInput(text: $name, placeholder: "например, Иван", label: "Имя", textContentType: .name)
                    AuthField(error: tagError) {
                        AppInput(text: $tag, placeholder: "например, ivan_trader", label: "Тег", error: tagError != nil)
                            .onChange(of: tag) { _ in tagError = nil }
                    }
                }
                .padding(.top, 24)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 78)
            .padding(.bottom, 40)
        }
        .ignoresSafeArea(.container, edges: .top)
        .overlay(alignment: .top) {
            HStack(alignment: .top) {
                GlassBackButton()
                Spacer()
                Button { save() } label: {
                    Text(saving ? "…" : "Готово")
                        .font(Theme.sans(13)).tracking(0.52)
                        .foregroundStyle(Theme.text)
                        .frame(width: 72, height: 42)
                        .liquidGlass(Capsule(), interactive: true)
                }
                .buttonStyle(PressableStyle())
                .disabled(saving)
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, Theme.topBarY)
            .ignoresSafeArea(.container, edges: .top)
        }
        .screenBackground()
        .animation(.easeOut(duration: 0.2), value: tagError)
        .onChange(of: photoItem) { _ in loadPicked() }
        .task { await load() }
    }

    private func load() async {
        name = session.user?.name ?? ""
        tag = session.user?.username ?? ""
        avatarUrl = session.user?.avatarUrl
        if let p = try? await Api.fetchProfile(), let u = p.user {
            name = u.name ?? name
            tag = u.username ?? tag
            avatarUrl = u.avatarUrl
        }
    }

    private func loadPicked() {
        guard let item = photoItem else { return }
        uploading = true
        Task { @MainActor in
            defer { photoItem = nil; uploading = false }
            guard let data = try? await item.loadTransferable(type: Data.self) else {
                AppToast.shared.show("Не удалось загрузить фото"); return
            }
            if let user = try? await Api.uploadAvatar(data: data, filename: "avatar.jpg", mime: "image/jpeg") {
                avatarUrl = user.avatarUrl
            } else {
                AppToast.shared.show("Не удалось загрузить фото")
            }
            await session.load(force: true)
        }
    }

    private func removePhoto() {
        guard !uploading else { return }
        uploading = true
        Task { @MainActor in
            defer { uploading = false }
            do {
                _ = try await Api.removeAvatar()
                avatarUrl = nil
            } catch {
                AppToast.shared.show("Не удалось удалить фото")
            }
            await session.load(force: true)
        }
    }

    private func save() {
        guard !saving else { return }
        saving = true
        let newName = name.trimmed
        let newTag = tag.trimmed
        Task { @MainActor in
            do {
                try await Api.updateProfile(name: newName, username: newTag.isEmpty ? nil : newTag)
                await session.load(force: true)
                Haptics.medium()
                router.pop()
            } catch let e as ApiError {
                Haptics.rigid()
                tagError = e.errors["username"]?.first ?? e.message
            } catch {
                Haptics.rigid()
            }
            saving = false
        }
    }
}
