<?php

namespace App\Http\Controllers;

use App\Models\Report;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

class ReportController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $query = Report::with('instrument')->latest('published_at');

        // Фильтр по инструменту (ключ: gold, wti, btc …) — главный экран.
        if ($instrument = $request->query('instrument')) {
            if ($instrument !== 'all') {
                $query->whereHas('instrument', fn ($q) => $q->where('key', $instrument));
            }
        }

        // Фильтр по статусу — панель фильтров аналитики.
        if ($statuses = $request->query('status')) {
            $query->whereIn('status', is_array($statuses) ? $statuses : explode(',', $statuses));
        }

        // Поиск по заголовку/описанию — экран аналитики.
        if ($search = $request->query('search')) {
            $query->where(function ($q) use ($search) {
                $q->where('title', 'like', "%{$search}%")
                    ->orWhere('description', 'like', "%{$search}%");
            });
        }

        $reports = $query->with('chat')->get()->map(fn (Report $r) => self::present($r));

        return response()->json(['data' => $reports]);
    }

    public function show(Report $report): JsonResponse
    {
        $report->load('instrument', 'chat');

        return response()->json(['data' => self::present($report, true)]);
    }

    /**
     * Обложка отчёта. Открыта без токена, чтобы её можно было показать в <img>.
     */
    public function cover(Report $report): BinaryFileResponse
    {
        abort_unless($report->cover_path && Storage::disk('local')->exists($report->cover_path), 404);

        return response()->file(Storage::disk('local')->path($report->cover_path), [
            'Cache-Control' => 'public, max-age=86400',
        ]);
    }

    /**
     * HTML-файл отчёта. Отдаём с CSP sandbox, чтобы загруженный html не мог
     * читать данные домена API (скрипты работают в изолированном origin).
     */
    public function html(Report $report): BinaryFileResponse
    {
        abort_unless($report->html_path && Storage::disk('local')->exists($report->html_path), 404);

        return response()->file(Storage::disk('local')->path($report->html_path), [
            'Content-Type' => 'text/html; charset=utf-8',
            'Content-Security-Policy' => 'sandbox allow-scripts',
            'X-Content-Type-Options' => 'nosniff',
        ]);
    }

    /**
     * Ссылки на файлы — пути относительно базы API (/api), фронт добавляет префикс сам.
     *
     * @return array<string, mixed>
     */
    public static function present(Report $report, bool $withBody = false): array
    {
        $version = $report->updated_at?->timestamp;
        $data = [
            'id' => $report->id,
            'title' => $report->title,
            'description' => $report->description,
            'badge' => $report->badge,
            'status' => $report->status,
            'date' => $report->published_at?->format('d.m.Y'),
            'material' => $report->instrument?->key,
            'instrument' => $report->instrument?->label,
            'chart_url' => $report->chart_url,
            'cover_url' => $report->cover_path ? "/reports/{$report->id}/cover?v={$version}" : null,
            'cover_name' => $report->cover_name,
            'html_url' => $report->html_path ? "/reports/{$report->id}/html?v={$version}" : null,
            'html_name' => $report->html_name,
            'chat' => $report->relationLoaded('chat') ? $report->chat?->slug : null,
        ];

        if ($withBody) {
            $data['body'] = $report->body;
        }

        return $data;
    }
}
