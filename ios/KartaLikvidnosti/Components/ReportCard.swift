import SwiftUI

/// Block/Карточка отчета: плашка «фон плашки», обложка 162, справа сверху —
/// бейдж статуса; заголовок Min Sans Bold 16, дата 14 «белый неактив»,
/// описание Regular 14, иконка материала 20 и кнопка «Перейти в отчет».
struct ReportCard: View {
    let report: ReportDto
    var actionLabel: String = "Перейти в отчет"
    let onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ZStack(alignment: .topTrailing) {
                ZStack {
                    ReportCoverPlaceholder()
                    if let url = fileURL(report.coverUrl) { RemoteImage(url: url) }
                }
                .frame(height: 162)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

                if let badge = report.badge, !badge.isEmpty {
                    Text(badge)
                        .font(Theme.sans(Theme.fontSm, .semibold))
                        .foregroundStyle(Theme.text)
                        .padding(.horizontal, 12).padding(.vertical, 4)
                        .background(Theme.badge, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                        .padding(16)
                }
            }

            VStack(alignment: .leading, spacing: 24) {
                HStack(alignment: .top, spacing: 0) {
                    VStack(alignment: .leading, spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(report.title)
                                .font(Theme.sans(Theme.fontMd, .bold))
                                .foregroundStyle(Theme.text)
                                .frame(maxWidth: 260, alignment: .leading)
                            if let date = report.date {
                                Text(date).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                            }
                        }
                        Text(report.description)
                            .font(Theme.sans(Theme.fontBase))
                            .foregroundStyle(Theme.text)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Icon(AppIcons.material(report.material), size: 17.5, color: Theme.text)
                        .frame(width: 20, height: 20)
                }

                AppButton(title: actionLabel, variant: .soft, action: onOpen)
            }
            .padding(.horizontal, 16)
        }
        .padding(.bottom, 16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}

/// Обложка из макета (заливка «Rectangle 7», режим Fill) — для отчётов без
/// своей картинки.
struct ReportCoverPlaceholder: View {
    var body: some View {
        Color.clear
            .overlay(Image("ReportCover").resizable().scaledToFill())
            .clipped()
    }
}
