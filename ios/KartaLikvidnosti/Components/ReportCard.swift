import SwiftUI

/// Карточка отчёта (home/ReportCard.vue).
struct ReportCard: View {
    let report: ReportDto
    var actionLabel: String = "Перейти в отчёт"
    let onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ZStack(alignment: .topTrailing) {
                Group {
                    if let url = fileURL(report.coverUrl) {
                        RemoteImage(url: url)
                    } else {
                        HoloFoil()
                    }
                }
                .frame(height: 162)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))

                if let badge = report.badge, !badge.isEmpty {
                    Text(badge)
                        .font(.system(size: Theme.fontSm, weight: .semibold))
                        .foregroundStyle(Theme.text)
                        .padding(.horizontal, 12).padding(.vertical, 4)
                        .background(Theme.badge)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
                        .padding(16)
                }
            }

            VStack(alignment: .leading, spacing: 24) {
                HStack(alignment: .top, spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(report.title).font(.system(size: Theme.fontMd, weight: .bold))
                            .foregroundStyle(Theme.text)
                        if let date = report.date {
                            Text(date).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Spacer(minLength: 8)
                    Icon(AppIcons.material(report.material), size: 20, color: Theme.text)
                }

                Text(report.description)
                    .font(.system(size: Theme.fontBase))
                    .foregroundStyle(Theme.text)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)

                AppButton(title: actionLabel, variant: .soft, action: onOpen)
            }
            .padding(.horizontal, 16)
        }
        .padding(.bottom, 16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}
