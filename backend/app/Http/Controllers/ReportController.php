<?php

namespace App\Http\Controllers;

use App\Models\Report;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

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

        $reports = $query->get()->map(fn (Report $r) => $this->toArray($r));

        return response()->json(['data' => $reports]);
    }

    public function show(Report $report): JsonResponse
    {
        $report->load('instrument');

        return response()->json(['data' => $this->toArray($report, true)]);
    }

    /**
     * @return array<string, mixed>
     */
    private function toArray(Report $report, bool $withBody = false): array
    {
        $data = [
            'id' => $report->id,
            'title' => $report->title,
            'description' => $report->description,
            'badge' => $report->badge,
            'status' => $report->status,
            'date' => $report->published_at?->format('d.m.Y'),
            'material' => $report->instrument?->key,
            'instrument' => $report->instrument?->label,
        ];

        if ($withBody) {
            $data['body'] = $report->body;
        }

        return $data;
    }
}
