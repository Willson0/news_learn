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

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Spacer()
                Button { save() } label: {
                    Text("Готово").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        .background(Theme.surfaceMuted).overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1)).clipShape(Capsule())
                }.buttonStyle(.plain).opacity(saving ? 0.6 : 1)
            }

            VStack(spacing: 14) {
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    avatarView.frame(width: 150, height: 150).clipShape(Circle())
                }
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    Text("Изменить фотографию").font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Название").font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                TextField("", text: $name).foregroundStyle(Theme.text).tint(Theme.green)
                    .onChange(of: name) { _ in error = "" }
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(Theme.surfaceMuted).overlay(RoundedRectangle(cornerRadius: Theme.radiusInput).stroke(Color.white.opacity(0.12), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))

            if !error.isEmpty { Text(error).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error).frame(maxWidth: .infinity, alignment: .leading) }
            Spacer()
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 40)
        .screenBackground(Theme.bg)
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
            Circle().fill(LinearGradient(colors: [Color(hex: "#d7cfe0"), Color(hex: "#a9adbd"), Color(hex: "#c4c7d2")],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
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
