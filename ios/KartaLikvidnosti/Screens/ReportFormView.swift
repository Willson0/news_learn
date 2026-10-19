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

    // «Создание и загрузка отчета» (Figma 349:11597, 349:11743, ошибки
    // 349:12486, редактирование 349:12705): «назад» (и красная корзина при
    // редактировании) на y=78, заголовок Kreadon 24 на y=144; поля со
    // стеклом и подписью: «Заголовок» 63, «Описание» 101 со счётчиком
    // «N/250 символов» справа, «График» 63; блок чипов-инструментов
    // (#25262A, радиус 24, отступ 12, шаг 4); строки файлов 64 («Отчет html»,
    // «Обложка»: белая иконка 32 с зелёным «+», пурпурная пилюля
    // «Добавить/Удалить»); ошибки 12 pt красным 70 % под полем; внизу кнопка.
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                Text(isEdit ? "Редактирование отчета" : "Создание отчета")
                    .font(Theme.heading(Theme.fontTitle)).foregroundStyle(Theme.text)
                    .padding(.bottom, 12)

                AuthField(error: errors["title"]) {
                    AppInput(text: $title, placeholder: "Введите текст", label: "Заголовок", height: 63)
                        .onChange(of: title) { _ in errors["title"] = nil }
                }

                AuthField(error: errors["description"]) { descriptionInput }

                AuthField(error: errors["chart_url"]) {
                    AppInput(text: $chartUrl, placeholder: "Ссылка на график", label: "График",
                             keyboard: .URL, height: 63)
                }

                AuthField(error: errors["instrument"]) { instrumentChips }

                AuthField(error: errors["html"]) {
                    fileRow(name: htmlName.isEmpty ? "Отчет html" : htmlName, has: !htmlName.isEmpty,
                            icon: { if htmlName.isEmpty { AnyView(addIcon) } else { AnyView(FileHtmlIcon(size: 30)) } },
                            add: { htmlImporter = true }, drop: { htmlData = nil; htmlName = ""; removeHtml = isEdit })
                }

                AuthField(error: errors["cover"]) { fileRowCover }

                if let e = errors["form"] { AuthFieldError(text: e) }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, 144)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AppButton(title: isEdit ? "Сохранить" : "Создать отчет", variant: .accent, disabled: saving) { submit() }
                .padding(.horizontal, Theme.screenX)
                .padding(.top, 12)
                .padding(.bottom, 45)
        }
        .ignoresSafeArea(.container, edges: [.top, .bottom])
        .overlay(alignment: .top) {
            HStack {
                GlassBackButton()
                Spacer()
                if isEdit {
                    GlassCircleButton(icon: AppIcons.trashX, iconSize: 24, iconColor: Theme.error) { confirmOpen = true }
                }
            }
            .padding(.horizontal, Theme.screenX)
            .padding(.top, Theme.topBarY)
            .ignoresSafeArea(.container, edges: .top)
        }
        .background(ScreenBackdrop())
        .toolbar(.hidden, for: .navigationBar)
        .animation(.easeOut(duration: 0.2), value: errors)
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

    private var descriptionInput: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Описание")
                Spacer()
                Text("\(descriptionText.count)/\(descriptionMax) символов")
            }
            .font(Theme.sans(Theme.fontSm)).foregroundStyle(Theme.textSecondary)
            TextField("", text: $descriptionText,
                      prompt: Text("Введите текст").foregroundColor(Theme.textMuted), axis: .vertical)
                .lineLimit(3...6)
                .font(Theme.sans(Theme.fontBase))
                .foregroundStyle(errors["description"] != nil ? Theme.error : Theme.text)
                .tint(Theme.green)
                .onChange(of: descriptionText) { v in
                    if v.count > descriptionMax { descriptionText = String(v.prefix(descriptionMax)) }
                    errors["description"] = nil
                }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 101, alignment: .top)
        .liquidGlass(RoundedRectangle(cornerRadius: Theme.radiusInput, style: .continuous))
    }

    private var addIcon: some View {
        Icon(AppIcons.plus, size: 24, color: Theme.green)
            .frame(width: 32, height: 32)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var instrumentChips: some View {
        FlowLayout(spacing: 4, lineSpacing: 4) {
            ForEach(instruments, id: \.key) { item in
                let on = instrument == item.key
                Button {
                    instrument = on ? nil : item.key
                    errors["instrument"] = nil; Haptics.light()
                } label: {
                    Text(item.label).font(Theme.sans(Theme.fontBase)).foregroundStyle(Theme.text)
                        .padding(.horizontal, 24).frame(height: 40)
                        .modifier(ChipFill(on: on))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private struct ChipFill: ViewModifier {
        let on: Bool
        func body(content: Content) -> some View {
            let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
            if on { content.liquidGlass(shape, fill: ActiveFill()) } else { content.liquidGlass(shape) }
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
                .frame(width: 32, height: 32).background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            } else { addIcon }
            Text(coverName.isEmpty ? "Обложка" : coverName).font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text).lineLimit(1)
            Spacer(minLength: 8)
            if coverName.isEmpty {
                PhotosPicker(selection: $coverItem, matching: .images) { pillLabel("Добавить") }
                    .buttonStyle(.plain)
            } else {
                PurplePillButton(title: "Удалить") { coverData = nil; coverName = ""; coverPreviewURL = nil; removeCover = isEdit }
            }
        }
        .padding(.horizontal, 16).frame(height: 64)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func fileRow(name: String, has: Bool, icon: () -> AnyView, add: @escaping () -> Void, drop: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            icon()
            Text(name).font(Theme.sans(Theme.fontMd)).foregroundStyle(Theme.text).lineLimit(1)
            Spacer(minLength: 8)
            PurplePillButton(title: has ? "Удалить" : "Добавить", action: has ? drop : add)
        }
        .padding(.horizontal, 16).frame(height: 64)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.radiusCard, style: .continuous))
    }

    private func pillLabel(_ t: String) -> some View {
        Text(t).font(Theme.sans(13)).tracking(0.52).foregroundStyle(Theme.text)
            .padding(.horizontal, 12).frame(height: 32)
            .liquidGlass(Capsule(), fill: PurpleFill())
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
