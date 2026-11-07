<?php

namespace App\Services;

use App\Models\Report;
use App\Models\User;
use Illuminate\Support\Facades\Log;

/**
 * Push «Вышел новый отчёт» подписчикам. Учитывает настройки уведомлений в профиле:
 * «Все уведомления» — о каждом отчёте; иначе «По инструментам» — только об отчётах
 * по отслеживаемым инструментам. Отчёт платный, поэтому только тем, у кого есть доступ.
 */
class ReportNotifier
{
    public function __construct(private PushService $push) {}

    /**
     * Отправляет уведомление после ответа сервера (админ не ждёт рассылку), один раз на отчёт.
     */
    public static function dispatchFor(Report $report): void
    {
        if ($report->status === 'draft' || $report->notified_at !== null) {
            return;
        }

        $id = $report->id;
        dispatch(fn () => app(self::class)->notify($id))->afterResponse();
    }

    public function notify(int $reportId): int
    {
        // Атомарная отметка «разослано»: повторная публикация или двойной вызов второй push не пришлют.
        $claimed = Report::whereKey($reportId)->where('status', '!=', 'draft')->whereNull('notified_at')
            ->update(['notified_at' => now()]);
        if ($claimed !== 1) {
            return 0;
        }
        $report = Report::with('instrument')->find($reportId);

        $users = User::query()
            ->whereHas('deviceTokens')
            ->where(fn ($q) => $q->where('notify_all', true)->orWhere('notify_by_instrument', true))
            ->with(['subscription', 'instruments', 'deviceTokens'])
            ->get()
            ->filter(fn (User $u) => $this->wants($u, $report) && $u->hasAccess());

        $tokens = $users->flatMap(fn (User $u) => $u->deviceTokens->pluck('token'))->unique()->values()->all();
        if ($tokens === []) {
            return 0;
        }

        $instrument = $report->instrument?->label;
        $title = $instrument ? "Новый отчёт · {$instrument}" : 'Новый отчёт';
        $body = trim($report->title.($report->description ? "\n".$report->description : ''));

        try {
            return $this->push->sendToTokens($tokens, $title, mb_strimwidth($body, 0, 240, '…'), [
                'type' => 'report',
                'report' => (string) $report->id,
            ]);
        } catch (\Throwable $e) {
            Log::warning('Push о новом отчёте не отправлен', ['report' => $report->id, 'error' => $e->getMessage()]);

            return 0;
        }
    }

    private function wants(User $user, Report $report): bool
    {
        if ($user->notify_all) {
            return true;
        }

        return $user->notify_by_instrument
            && $report->instrument_id !== null
            && $user->instruments->contains('id', $report->instrument_id);
    }
}
