import SwiftUI
import PhotosUI

struct ChatEditView: View {
    @EnvironmentObject var router: Router
    let chatId: String

    @State private var name = ""
    @State private var previewData: Data? = nil
    @State private var previewURL: URL? = nil
    @State private var pickerItem: PhotosPickerItem? = nil
    @State private var error = ""
    @State private var saving = false

    // «Чат Инфа Настройки для админа» (Figma 337:36498, 349:12314): как
    // «Профиль изменить» — «назад» и стеклянная пилюля 72×42 на y=78, аватар
    // 148 с y=78, «Изменить фотографию» 14 pt, поле «Имя» с подписью (64 pt).
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    VStack(spacing: 12) {
                        avatarView.frame(width: 148, height: 148).clipShape(Circle())
                        Text("Изменить фотографию").font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                    }
                }
                .buttonStyle(.plain)

                AuthField(error: error.isEmpty ? nil : error) {
                    AppInput(text: $name, placeholder: "Название чата", label: "Имя", error: !error.isEmpty)
                        .onChange(of: name) { _ in error = "" }
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
        .animation(.easeOut(duration: 0.2), value: error)
        .task { await load() }
        .onChange(of: pickerItem) { item in
            Task { if let data = try? await item?.loadTransferable(type: Data.self) { previewData = data; previewURL = nil } }
        }
    }

    @ViewBuilder private var avatarView: some View {
        if let data = previewData, let img = UIImage(data: data) {
            Image(uiImage: img).resizable().aspectRatio(contentMode: .fill)
        } else if let url = previewURL {
            RemoteImage(url: url)
        } else {
            ReportCoverPlaceholder()
        }
    }

    private func load() async {
        do {
            let resp = try await Api.fetchMessages(chatId)
            name = resp.chat?.title ?? ""
            previewURL = fileURL(resp.chat?.avatarUrl)
        } catch {}
    }

    private func save() {
        guard !name.trimmed.isEmpty else { self.error = "Напишите название"; return }
        saving = true; error = ""
        Task {
            do {
                let avatar: (data: Data, filename: String, mime: String)? = previewData.map { (data: $0, filename: "avatar.jpg", mime: "image/jpeg") }
                try await Api.updateChat(slug: chatId, title: name.trimmed, avatar: avatar)
                Haptics.medium()
                router.pop()
            } catch let e as ApiError {
                error = e.fieldError("title", "avatar") ?? e.message
            } catch { self.error = "Не удалось сохранить" }
            saving = false
        }
    }
}
