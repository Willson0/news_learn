<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Admin\ReportController as AdminReportController;
use App\Http\Controllers\ReportController as PublicReportController;
use App\Models\Report;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Отчёты в админке: весь список (включая черновики), создание, правки и статус публикации.
 * Создание, изменение и удаление наследуются от мобильного админ-API.
 */
class ReportController extends AdminReportController
{
    public function index(): JsonResponse
    {
        $reports = Report::with(['instrument', 'chat'])->latest('updated_at')->get()
            ->map(fn (Report $r) => PublicReportController::present($r, true) + [
                'updated_at' => $r->updated_at?->format('d.m.Y'),
            ]);

        return response()->json(['data' => $reports->values()]);
    }

    /** Публикация: «actual» — показан пользователям, «draft» — только в админке. */
    public function status(Request $request, Report $report): JsonResponse
    {
        $data = $request->validate([
            'status' => ['required', 'in:actual,draft'],
        ]);

        $report->update(['status' => $data['status']]);

        return response()->json(['data' => PublicReportController::present($report->fresh(['instrument', 'chat']), true)]);
    }
}
