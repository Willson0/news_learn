import SwiftUI

/// Block/Карточка отчета: плашка «фон плашки», обложка 162, справа сверху —
/// бейдж статуса; заголовок Min Sans Bold 16, дата 14 «белый неактив»,
/// описание Regular 14 (не больше трёх строк), иконка материала 20.
/// Нажимается вся карточка, справа внизу — шеврон.
struct ReportCard: View {
    let report: ReportDto
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) { content }
            .buttonStyle(PressableStyle())
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityHint("Открыть отчет")
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 16) {
            ZStack(alignment: .topTrailing) {
                ZStack {
                    // Обложка есть — пока грузится, мигающий блок (без промелькивания заглушки).
                    if let url = fileURL(report.coverUrl) {
                        RemoteImage(url: url) { ReportCoverPlaceholder() }
                    } else {
                        ReportCoverPlaceholder()
                    }
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
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    VStack(spacing: 0) {
                        Icon(AppIcons.material(report.material), size: 17.5, color: Theme.text)
                            .frame(width: 20, height: 20)
                        Spacer(minLength: 8)
                        Icon(AppIcons.back, size: 16, color: Theme.textSecondary).scaleEffect(x: -1, y: 1)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.bottom, 16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }
}

/// Обложка из макета (заливка «Rectangle 7», режим Fill) — для отчётов без
/// своей картинки.
struct ReportCoverPlaceholder: View {
    var body: some View {
        Color.clear
            .overlay(Image("ReportCover").resizable().scaledToFill())
            .clipped()
            .allowsHitTesting(false)
    }
}
