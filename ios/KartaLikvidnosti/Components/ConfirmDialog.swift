import SwiftUI

/// Модальное подтверждение (admin/ConfirmDialog.vue).
struct ConfirmDialog: View {
    let title: String
    var text: String = ""
    var confirmLabel: String = "Удалить"
    var cancelLabel: String = "Отмена"
    var busy: Bool = false
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.25).ignoresSafeArea()
                .onTapGesture { onCancel() }
            VStack(spacing: 8) {
                Text(title).font(Theme.sans(Theme.fontMd, .bold))
                    .foregroundStyle(Theme.text)
                    .multilineTextAlignment(.center)
                if !text.isEmpty {
                    Text(text)
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(3)
                        .padding(.bottom, 8)
                }
                Button(action: { if !busy { onConfirm() } }) {
                    Text(confirmLabel).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity).frame(height: 48)
                        .background(LinearGradient(colors: [Color(hex: "#8c1b1b"), Color(hex: "#6e1212")],
                                                   startPoint: .top, endPoint: .bottom))
                        .clipShape(Capsule())
                }
                .opacity(busy ? 0.6 : 1)
                Button(action: onCancel) {
                    Text(cancelLabel).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                        .frame(maxWidth: .infinity).frame(height: 48)
                        .overlay(Capsule().stroke(Color.white.opacity(0.35), lineWidth: 1))
                }
            }
            .padding(16)
            .frame(maxWidth: 276)
            .background(Color(hex: "#202124").opacity(0.92))
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(.horizontal, 60)
        }
    }
}
