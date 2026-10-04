import SwiftUI

struct ProfileEditView: View {
    @EnvironmentObject var router: Router
    @State private var name = "Артем"
    @State private var tag = "@Artemis"

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Spacer()
                Button { Haptics.medium(); router.pop() } label: {
                    Text("Готово").font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 22).padding(.vertical, 11)
                        .background(Theme.surfaceMuted).overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1)).clipShape(Capsule())
                }.buttonStyle(.plain)
            }

            VStack(spacing: 14) {
                Circle().fill(Color.white).frame(width: 148, height: 148)
                Button("Изменить фотографию") {}.font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text)
            }.padding(.vertical, 8)

            VStack(spacing: 12) {
                labeledField("Имя", $name)
                labeledField("Тег", $tag)
            }
            Spacer()
        }
        .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 40)
        .screenBackground(Theme.bg)
    }

    private func labeledField(_ label: String, _ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
            TextField("", text: text).foregroundStyle(Theme.text).tint(Theme.green)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Theme.surfaceMuted).overlay(RoundedRectangle(cornerRadius: Theme.radiusInput).stroke(Color.white.opacity(0.08), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
    }
}
