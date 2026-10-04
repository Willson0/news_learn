import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct ReportFormView: View {
    @EnvironmentObject var router: Router
    let reportId: Int?

    @State private var title = ""
    @State private var descriptionText = ""
    @State private var chartUrl = ""
    @State private var instrument: String? = nil
    @State private var instruments: [(key: String, label: String)] = []

    @State private var coverData: Data? = nil
    @State private var coverName = ""
    @State private var coverPreviewURL: URL? = nil
    @State private var removeCover = false
    @State private var coverItem: PhotosPickerItem? = nil

    @State private var htmlData: Data? = nil
    @State private var htmlName = ""
    @State private var removeHtml = false
    @State private var htmlImporter = false

    @State private var errors: [String: String] = [:]
    @State private var saving = false
    @State private var confirmOpen = false
    @State private var deleting = false

    private let descriptionMax = 250
    private var isEdit: Bool { reportId != nil }
    private let shortLabels = ["gas": "Нат. газ"]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Spacer()
                    if isEdit {
                        Button { confirmOpen = true } label: {
                            Icon(AppIcons.trashX, size: 22, color: Color(hex: "#e0262b"))
                                .frame(width: 42, height: 42).background(Theme.surfaceMuted)
                                .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1)).clipShape(Circle())
                        }.buttonStyle(.plain)
                    }
                }.frame(minHeight: 42)

                Text(isEdit ? "Редактирование отчета" : "Создание отчета")
                    .font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text).padding(.vertical, 4)

                labeledInput(title: "Заголовок", error: errors["title"]) {
                    TextField("", text: $title, prompt: Text("Введите текст").foregroundColor(Theme.textMuted))
                        .onChange(of: title) { _ in errors["title"] = nil }
                }

                field(error: errors["description"]) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("Описание   \(descriptionText.count)/\(descriptionMax) символов")
                            .font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                        TextField("Введите текст", text: $descriptionText, axis: .vertical)
                            .lineLimit(3...6).foregroundStyle(Theme.text)
                            .onChange(of: descriptionText) { v in
                                if v.count > descriptionMax { descriptionText = String(v.prefix(descriptionMax)) }
                                errors["description"] = nil
                            }
                    }
                    .fieldChrome()
                }

                labeledInput(title: "График", error: errors["chart_url"]) {
                    TextField("", text: $chartUrl, prompt: Text("Ссылка на график").foregroundColor(Theme.textMuted))
                        .keyboardType(.URL).autocorrectionDisabled().textInputAutocapitalization(.never)
                }

                instrumentChips

                fileRow(name: htmlName.isEmpty ? "Отчет html" : htmlName, has: !htmlName.isEmpty,
                        icon: { if htmlName.isEmpty { AnyView(addIcon) } else { AnyView(FileHtmlIcon(size: 32)) } },
                        add: { htmlImporter = true }, drop: { htmlData = nil; htmlName = ""; removeHtml = isEdit })
                if let e = errors["html"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }

                fileRowCover

                if let e = errors["cover"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) }

                if let e = errors["form"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error).frame(maxWidth: .infinity, alignment: .center) }

                AppButton(title: isEdit ? "Сохранить" : "Создать отчет", variant: .accent, disabled: saving) { submit() }
                    .padding(.top, 20)
            }
            .padding(.horizontal, Theme.screenX).padding(.top, 16).padding(.bottom, 32)
        }
        .screenBackground(Theme.bg)
        .task { await load() }
        .onChange(of: coverItem) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self) {
                    coverData = data; coverName = "cover.jpg"; coverPreviewURL = nil; removeCover = false; errors["cover"] = nil
                }
            }
        }
        .fileImporter(isPresented: $htmlImporter, allowedContentTypes: [.html]) { result in
            if case .success(let url) = result {
                if url.startAccessingSecurityScopedResource() {
                    defer { url.stopAccessingSecurityScopedResource() }
                    htmlData = try? Data(contentsOf: url)
                    htmlName = url.lastPathComponent
                    removeHtml = false; errors["html"] = nil
                }
            }
        }
        .overlay {
            if confirmOpen {
                ConfirmDialog(title: "Удалить отчет?", text: "Удалив отчет он пропадет у всех пользователей, включая чат",
                              busy: deleting, onConfirm: { remove() }, onCancel: { confirmOpen = false })
            }
        }
    }

    private var addIcon: some View {
        Icon(AppIcons.plus, size: 22, color: Color(hex: "#6fbf1f"))
            .frame(width: 32, height: 32).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 6))
    }

    private var instrumentChips: some View {
        FlowLayout(spacing: 6) {
            ForEach(instruments, id: \.key) { item in
                Button {
                    instrument = instrument == item.key ? nil : item.key
                    errors["instrument"] = nil; Haptics.light()
                } label: {
                    Text(item.label).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 16).frame(height: 40)
                        .background(instrument == item.key ? AnyShapeStyle(Theme.gradientAccent) : AnyShapeStyle(Color.clear),
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(instrument == item.key ? 0.25 : 0.18), lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(12).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(alignment: .bottomLeading) {
            if let e = errors["instrument"] { Text(e).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error).offset(y: 22) }
        }
    }

    private var fileRowCover: some View {
        HStack(spacing: 12) {
            if !coverName.isEmpty {
                Group {
                    if let data = coverData, let img = UIImage(data: data) { Image(uiImage: img).resizable().aspectRatio(contentMode: .fill) }
                    else if let url = coverPreviewURL { RemoteImage(url: url) }
                    else { Color.white }
                }
                .frame(width: 32, height: 32).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 6))
            } else { addIcon }
            Text(coverName.isEmpty ? "Обложка" : coverName).font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text).lineLimit(1)
            Spacer()
            if coverName.isEmpty {
                PhotosPicker(selection: $coverItem, matching: .images) { pillLabel("Добавить") }
            } else {
                Button { coverData = nil; coverName = ""; coverPreviewURL = nil; removeCover = isEdit } label: { pillLabel("Удалить") }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16).frame(height: 64).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func fileRow(name: String, has: Bool, icon: () -> AnyView, add: @escaping () -> Void, drop: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            icon()
            Text(name).font(.system(size: Theme.fontMd)).foregroundStyle(Theme.text).lineLimit(1)
            Spacer()
            Button(action: has ? drop : add) { pillLabel(has ? "Удалить" : "Добавить") }.buttonStyle(.plain)
        }
        .padding(.horizontal, 16).frame(height: 64).background(Theme.surface).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func pillLabel(_ t: String) -> some View {
        Text(t).font(.system(size: Theme.fontBase)).foregroundStyle(Theme.text)
            .padding(.horizontal, 12).frame(height: 32).background(Theme.gradientPurpleSolid).clipShape(Capsule())
    }

    private func field<C: View>(error: String?, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) { content(); if let error = error { Text(error).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.error) } }
    }

    private func labeledInput<C: View>(title: String, error: String?, @ViewBuilder _ content: () -> C) -> some View {
        field(error: error) {
            VStack(alignment: .trailing, spacing: 2) {
                Text(title).font(.system(size: Theme.fontSm)).foregroundStyle(Theme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                content().foregroundStyle(Theme.text).tint(Theme.green)
            }
            .fieldChrome()
        }
    }

    private func load() async {
        do {
            let list = try await Api.fetchInstruments()
            instruments = list.map { (key: $0.key, label: shortLabels[$0.key] ?? $0.label) }
        } catch { instruments = [] }
        guard let id = reportId else { return }
        do {
            guard let r = try await Api.fetchReport(id: String(id)) else { router.popToRoot(); return }
            title = r.title; descriptionText = r.description; chartUrl = r.chartUrl ?? ""
            instrument = r.material
            htmlName = r.htmlName ?? ""; coverName = r.coverName ?? ""
            coverPreviewURL = fileURL(r.coverUrl)
        } catch { router.popToRoot() }
    }

    private func submit() {
        var e: [String: String] = [:]
        if title.trimmed.isEmpty { e["title"] = "Напишите название" }
        if descriptionText.trimmed.isEmpty { e["description"] = "Напишите описание" }
        if instrument == nil { e["instrument"] = "Выберите инструмент" }
        if coverName.isEmpty { e["cover"] = "Загрузите обложку" }
        errors = e
        if !e.isEmpty { Haptics.heavy(); return }

        var payload = ReportFormPayload(title: title.trimmed, description: descriptionText.trimmed,
                                        chartUrl: chartUrl.trimmed, instrument: instrument ?? "")
        payload.cover = coverData.map { (data: $0, filename: coverName.isEmpty ? "cover.jpg" : coverName, mime: "image/jpeg") }
        payload.html = htmlData.map { (data: $0, filename: htmlName.isEmpty ? "report.html" : htmlName, mime: "text/html") }
        payload.removeCover = removeCover
        payload.removeHtml = removeHtml

        saving = true
        Task {
            do {
                let report = isEdit ? try await Api.updateReport(id: reportId!, payload) : try await Api.createReport(payload)
                Haptics.medium()
                if let report = report {
                    router.pop()
                    router.push(.reportDetail(id: report.id))
                } else { router.pop() }
            } catch let err as ApiError {
                var fe: [String: String] = [:]
                for (k, v) in err.errors { fe[k] = v.first }
                if fe.isEmpty { fe["form"] = err.message }
                errors = fe; Haptics.heavy()
            } catch { errors = ["form": "Не удалось сохранить"] }
            saving = false
        }
    }

    private func remove() {
        guard let id = reportId else { return }
        deleting = true
        Task {
            do { try await Api.deleteReport(id: id); Haptics.medium(); router.popToRoot(); router.switchTab(.analytics) }
            catch let e as ApiError { errors = ["form": e.message]; confirmOpen = false }
            catch { errors = ["form": "Не удалось удалить"]; confirmOpen = false }
            deleting = false
        }
    }
}

private extension View {
    func fieldChrome() -> some View {
        self.padding(.horizontal, 16).padding(.vertical, 10)
            .background(Theme.surfaceMuted)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.12), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}
