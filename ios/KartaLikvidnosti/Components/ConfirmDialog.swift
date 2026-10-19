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

    // Попап «Удалить отчет?» (Figma 349:12705): стекло «чёрный 2», радиус 24,
    // заголовок Bold 16 и текст 14 по левому краю, красная «Удалить» и
    // стеклянная «Отмена» по 48; фон затемняется и размывается.
    var body: some View {
        ZStack {
            ZStack { BackdropBlur(radius: 6); Color.black.opacity(0.35) }
                .ignoresSafeArea()
                .onTapGesture { onCancel() }
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(Theme.sans(Theme.fontMd, .bold))
                    .foregroundStyle(Theme.text)
                if !text.isEmpty {
                    Text(text)
                        .font(Theme.sans(Theme.fontBase))
                        .foregroundStyle(Theme.text)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 4)
                }
                VStack(spacing: 8) {
                    AppButton(title: confirmLabel, variant: .danger, disabled: busy) { onConfirm() }
                    AppButton(title: cancelLabel, variant: .secondary) { onCancel() }
                }
                .padding(.top, 16)
            }
            .padding(16)
            .frame(width: 243)
            .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous), fill: Theme.black2)
            .transition(.scale(scale: 0.92).combined(with: .opacity))
        }
    }
}
