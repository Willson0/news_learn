<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\Payment;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

/**
 * Аналитика: продажи (оплаты за период с итогами) и общая статистика пользователей.
 */
class AnalyticsController extends Controller
{
    /**
     * Оплаты за период. По умолчанию — текущий месяц.
     */
    public function sales(Request $request): JsonResponse
    {
        $data = $request->validate([
            'from' => ['sometimes', 'date'],
            'to' => ['sometimes', 'date', 'after_or_equal:from'],
            'page' => ['sometimes', 'integer', 'min:1'],
        ]);

        $from = Carbon::parse($data['from'] ?? now()->startOfMonth())->startOfDay();
        $to = Carbon::parse($data['to'] ?? now())->endOfDay();

        $payments = Payment::with('user')
            ->whereBetween('paid_at', [$from, $to])
            ->orderByDesc('paid_at')
            ->get();

        $series = $payments->groupBy(fn (Payment $p) => $p->paid_at->format('Y-m'))
            ->sortKeys()
            ->map(fn ($group, $month) => [
                'label' => Carbon::createFromFormat('Y-m', $month)->translatedFormat('M Y'),
                'total' => (int) $group->sum('amount'),
                'count' => $group->count(),
            ])->values();

        $total = (int) $payments->sum('amount');
        $count = $payments->count();

        $items = $payments->forPage((int) ($data['page'] ?? 1), 25)->values()->map(fn (Payment $p) => [
            'id' => $p->id,
            'user' => $p->user?->name ?? 'Удалён',
            'kind' => $p->kind === 'invoice' ? 'Счёт' : 'Автопродление',
            'amount' => $p->amount,
            'months' => $p->months,
            'paid_at' => $p->paid_at->format('d.m.Y H:i'),
        ]);

        return response()->json([
            'summary' => [
                'total' => $total,
                'count' => $count,
                'average' => $count ? (int) round($total / $count) : 0,
                'from' => $from->toDateString(),
                'to' => $to->toDateString(),
            ],
            'series' => $series,
            'data' => $items,
            'meta' => ['total' => $count, 'page' => (int) ($data['page'] ?? 1), 'last_page' => max(1, (int) ceil($count / 25))],
        ]);
    }

    /**
     * Статистика: регистрации по неделям, подписки по группам, оплаты по месяцам за полгода.
     */
    public function stats(): JsonResponse
    {
        $weeks = collect(range(7, 0))->map(function (int $back) {
            $start = now()->startOfWeek()->subWeeks($back);
            $end = $start->copy()->endOfWeek();
            $count = User::whereBetween('created_at', [$start, $end])->count();

            return ['label' => $start->format('d.m'), 'value' => $count];
        })->values();

        $users = User::with('subscription')->get();
        $groups = $users->groupBy(fn (User $u) => $this->groupOf($u));

        $months = collect(range(5, 0))->map(function (int $back) {
            $start = now()->startOfMonth()->subMonths($back);
            $sum = (int) Payment::whereBetween('paid_at', [$start, $start->copy()->endOfMonth()])->sum('amount');

            return ['label' => $start->translatedFormat('M'), 'value' => $sum];
        })->values();

        return response()->json([
            'registrations' => $weeks,
            'subscriptions' => collect(['active' => 'Активна', 'expired' => 'Истекла', 'free' => 'Без подписки'])
                ->map(fn (string $label, string $key) => [
                    'key' => $key,
                    'label' => $label,
                    'count' => $groups->get($key, collect())->count(),
                ])->values(),
            'revenue' => $months,
            'users_total' => $users->count(),
        ]);
    }

    private function groupOf(User $user): string
    {
        /** @var Subscription|null $sub */
        $sub = $user->subscription;
        if ($sub?->grantsAccessNow()) {
            return 'active';
        }

        return $sub && $sub->until ? 'expired' : 'free';
    }
}
