import SwiftUI

struct ProfileEditView: View {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session

    @State private var name = ""
    @State private var tag = ""
    @State private var avatar: URL? = nil
    @State private var saving = false

    private var avatarInitial: String {
        let base = tag.replacingOccurrences(of: "@", with: "")
        let src = base.isEmpty ? name : base
        return src.isEmpty ? "" : String(src.prefix(1)).uppercased()
    }

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Spacer()
                Button { save() } label: {
                    Text("Готово").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 22).padding(.vertical, 11)
                        .background(Theme.surfaceMuted).overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1)).clipShape(Capsule())
                }.buttonStyle(.plain).opacity(saving ? 0.6 : 1).disabled(saving)
            }

            VStack(spacing: 14) {
                AvatarCircle(url: avatar, size: 148, initial: avatarInitial)
                    .overlay(Circle().stroke(Color.white.opacity(0.08), lineWidth: 1))
                Button("Изменить фотографию") {}
                    .font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
            }.padding(.vertical, 8)

            VStack(spacing: 12) {
                labeledField("Имя", $name)
                labeledField("Тег", $tag)
            }
            Spacer()
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 40)
        .screenBackground(Theme.bg)
        .task { await load() }
    }

    private func labeledField(_ label: String, _ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
            TextField("", text: text).foregroundStyle(Theme.text).tint(Theme.green)
                .autocorrectionDisabled()
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Theme.surfaceMuted).overlay(RoundedRectangle(cornerRadius: Theme.radiusInput).stroke(Color.white.opacity(0.08), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
    }

    private func load() async {
        name = session.user?.name ?? ""
        tag = session.user?.username ?? ""
        avatar = fileURL(session.user?.avatarUrl)
        if let p = try? await Api.fetchProfile(), let u = p.user {
            name = u.name ?? name
            tag = u.username ?? tag
            if let a = u.avatarUrl { avatar = fileURL(a) }
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
            } catch {
                Haptics.rigid()
            }
            saving = false
        }
    }
}
