<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Http\Controllers\ReportController as PublicReportController;
use App\Models\Chat;
use App\Models\Instrument;
use App\Models\Report;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

/**
 * Создание, редактирование и удаление отчётов админом.
 * Файлы (обложка и html-отчёт) приходят multipart-формой, поэтому обновление — POST.
 */
class ReportController extends Controller
{
    private const DISK = 'local';

    public function store(Request $request): JsonResponse
    {
        $data = $this->validated($request, true);

        $report = DB::transaction(function () use ($request, $data) {
            $report = new Report([
                'title' => $data['title'],
                'description' => $data['description'],
                'body' => $data['description'],
                'chart_url' => $data['chart_url'] ?? null,
                'instrument_id' => $this->instrumentId($data['instrument']),
                'badge' => 'Актуальный',
                'status' => 'actual',
                'published_at' => now()->toDateString(),
            ]);
            $report->save();
            $this->storeFiles($request, $report);
            $report->save();

            // У каждого отчёта свой чат в разделе «Чаты по отчётам».
            Chat::create([
                'slug' => 'report-'.$report->id,
                'title' => $report->title,
                'type' => 'report',
                'section' => 'reports',
                'subtitle' => null,
                'report_id' => $report->id,
                'position' => (int) Chat::max('position') + 1,
            ]);

            return $report;
        });

        return response()->json(['data' => PublicReportController::present($report->fresh(['instrument', 'chat']), true)], 201);
    }

    public function update(Request $request, Report $report): JsonResponse
    {
        $data = $this->validated($request, false);

        $report->fill([
            'title' => $data['title'],
            'description' => $data['description'],
            'chart_url' => $data['chart_url'] ?? null,
            'instrument_id' => $this->instrumentId($data['instrument']),
        ]);
        if ($report->body === null || $report->isDirty('description')) {
            $report->body = $data['description'];
        }

        // «Удалить» у прикреплённого файла в форме редактирования.
        if ($request->boolean('remove_html')) {
            $this->deleteFile($report->html_path);
            $report->html_path = null;
            $report->html_name = null;
        }
        if ($request->boolean('remove_cover')) {
            $this->deleteFile($report->cover_path);
            $report->cover_path = null;
            $report->cover_name = null;
        }

        $this->storeFiles($request, $report);
        $report->save();

        return response()->json(['data' => PublicReportController::present($report->fresh(['instrument', 'chat']), true)]);
    }

    /**
     * Удаляет отчёт у всех пользователей вместе с его чатом и файлами.
     */
    public function destroy(Report $report): JsonResponse
    {
        $this->deleteFile($report->cover_path);
        $this->deleteFile($report->html_path);
        $report->delete(); // чат удаляется каскадом (chats.report_id)

        return response()->json(['status' => 'ok']);
    }

    /**
     * @return array<string, mixed>
     */
    private function validated(Request $request, bool $creating): array
    {
        return $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'description' => ['required', 'string', 'max:250'],
            'chart_url' => ['nullable', 'string', 'max:2048'],
            'instrument' => ['required', 'string', 'exists:instruments,key'],
            'html' => ['nullable', 'file', 'extensions:html,htm', 'max:10240'],
            'cover' => [$creating ? 'required' : 'nullable', 'image', 'max:8192'],
        ], [
            'title.required' => 'Напишите название',
            'description.required' => 'Напишите описание',
            'description.max' => 'Не больше 250 символов',
            'instrument.required' => 'Выберите инструмент',
            'instrument.exists' => 'Выберите инструмент',
            'html.extensions' => 'Нужен файл .html',
            'cover.required' => 'Загрузите обложку',
            'cover.image' => 'Обложка должна быть изображением',
        ]);
    }

    private function instrumentId(string $key): ?int
    {
        return Instrument::where('key', $key)->value('id');
    }

    private function storeFiles(Request $request, Report $report): void
    {
        if ($request->file('cover') instanceof UploadedFile) {
            $this->deleteFile($report->cover_path);
            $report->cover_path = $request->file('cover')->store('reports/covers', self::DISK);
            $report->cover_name = $request->file('cover')->getClientOriginalName();
        }
        if ($request->file('html') instanceof UploadedFile) {
            $this->deleteFile($report->html_path);
            $report->html_path = $request->file('html')->store('reports/html', self::DISK);
            $report->html_name = $request->file('html')->getClientOriginalName();
        }
    }

    private function deleteFile(?string $path): void
    {
        if ($path) {
            Storage::disk(self::DISK)->delete($path);
        }
    }
}
