<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\AppSetting;
use App\Models\Invoice;
use App\Models\Report;
use App\Models\User;
use App\Services\InvoiceService;
use Illuminate\Support\Carbon;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;
use RuntimeException;

/**
 * Пользователи в админке: краткая таблица, подробности, счета и условия подписки.
 */
class UserController extends Controller
{
    public function __construct(private InvoiceService $invoices) {}

    public function index(Request $request): JsonResponse
    {
        $query = User::with('subscription')->latest('created_at');

        if ($group = $request->query('filter')) {
            $this->applyGroup($query, $group);
        }

        if ($q = trim((string) $request->query('q', ''))) {
            $query->where(function (Builder $w) use ($q) {
                $w->where('name', 'like', "%{$q}%")
                    ->orWhere('email', 'like', "%{$q}%")
                    ->orWhere('username', 'like', "%{$q}%")
                    ->orWhere('phone', 'like', "%{$q}%");
            });
        }

        $users = $query->paginate(25);
        $reports = Report::where('status', '!=', 'draft')->count();

        return response()->json([
            'data' => $users->getCollection()->map(fn (User $u) => $this->row($u, $reports))->values(),
            'meta' => ['total' => $users->total(), 'page' => $users->currentPage(), 'last_page' => $users->lastPage()],
        ]);
    }

    public function show(User $user): JsonResponse
    {
        $user->load('subscription');

        return response()->json(['data' => $this->detail($user)]);
    }

    /**
     * Выставляет счёт. Сумма — из запроса или цена по умолчанию из настроек.
     */
    public function invoice(Request $request, User $user): JsonResponse
    {
        $user->load('subscription');
        if ($this->group($user) === 'active') {
            throw ValidationException::withMessages(['user' => 'У пользователя уже есть активная подписка']);
        }

        $data = $request->validate([
            'amount' => ['nullable', 'integer', 'min:1', 'max:1000000'],
            'months' => ['required', 'integer', 'min:1', 'max:12'],
        ]);

        $amount = $data['amount'] ?? AppSetting::defaultPrice();

        try {
            $invoice = $this->invoices->issue($user, $amount, $data['months'], $request->user('panel')->login);
        } catch (RuntimeException $e) {
            return response()->json(['message' => $e->getMessage()], 503);
        }

        return response()->json(['data' => [
            'id' => $invoice->id,
            'amount' => $invoice->amount,
            'months' => $invoice->months,
            'status' => $invoice->status,
            'confirmation_url' => $invoice->confirmation_url,
        ]], 201);
    }

    /**
     * Бесплатный доступ до даты, без счёта. Работает так же, как оплаченная подписка в приложении.
     */
    public function grantFree(Request $request, User $user): JsonResponse
    {
        $data = $request->validate([
            'until' => ['required', 'date', 'after:today'],
        ], [
            'until.after' => 'Дата окончания должна быть в будущем',
        ]);

        $this->invoices->grantFree($user, Carbon::parse($data['until']));

        return response()->json(['data' => $this->detail($user->fresh('subscription'))]);
    }

    /**
     * Меняет условия подписки: сумма, срок, дата окончания, автопродление, активность.
     */
    public function updateSubscription(Request $request, User $user): JsonResponse
    {
        $data = $request->validate([
            'amount' => ['sometimes', 'integer', 'min:1', 'max:1000000'],
            'months' => ['sometimes', 'integer', 'min:1', 'max:12'],
            'until' => ['sometimes', 'nullable', 'date'],
            'auto_pay' => ['sometimes', 'boolean'],
            'active' => ['sometimes', 'boolean'],
        ]);

        $this->invoices->changeTerms($user, $data);

        return response()->json(['data' => $this->detail($user->fresh('subscription'))]);
    }

    /** Фильтр по группе; совпадает с группой из group(). */
    private function applyGroup(Builder $query, string $group): void
    {
        $activeNow = fn (Builder $s) => $s->grantingAccess();

        match ($group) {
            'active' => $query->whereHas('subscription', $activeNow),
            'expired' => $query->whereHas('subscription', fn (Builder $s) => $s->whereNotNull('until')->whereNot($activeNow)),
            'free' => $query->whereDoesntHave('subscription', fn (Builder $s) => $s->whereNotNull('until')),
            default => null,
        };
    }

    /** Группа пользователя: active | expired | free. */
    public function group(User $user): string
    {
        $sub = $user->subscription;
        if ($sub?->grantsAccessNow()) {
            return 'active';
        }

        return $sub && $sub->until ? 'expired' : 'free';
    }

    /**
     * @return array<string, mixed>
     */
    private function row(User $user, int $reports): array
    {
        $sub = $user->subscription;
        $group = $this->group($user);

        return [
            'id' => $user->id,
            'name' => $user->name,
            'email' => $user->email,
            'telegram_id' => $user->telegram_id,
            'group' => $group,
            'status' => match ($group) {
                'active' => 'Активна',
                'expired' => 'Истекла',
                default => 'Без подписки',
            },
            'plan' => $sub && ($sub->active || $sub->until) ? number_format($sub->amount ?? AppSetting::defaultPrice(), 0, ',', ' ').' ₽ / '.($sub->months ?? AppSetting::defaultMonths()).' мес.' : 'Нет подписки',
            'until' => $sub?->until?->format('d.m.Y'),
            'reports' => $reports,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function detail(User $user): array
    {
        $sub = $user->subscription;

        return [
            'id' => $user->id,
            'name' => $user->name,
            'email' => $user->email,
            'phone' => $user->phone,
            'username' => $user->username,
            'telegram_id' => $user->telegram_id,
            'registered_at' => $user->created_at?->format('d.m.Y'),
            'last_seen_at' => $user->last_active_at?->format('d.m.Y H:i'),
            'group' => $this->group($user),
            'reports' => Report::where('status', '!=', 'draft')->count(),
            'subscription' => [
                // active — переключатель «доступ открыт» для формы; access_now — итог по общему правилу с датой.
                'active' => (bool) $sub?->active,
                'access_now' => (bool) $sub?->grantsAccessNow(),
                'amount' => $sub?->amount ?? AppSetting::defaultPrice(),
                'months' => $sub?->months ?? AppSetting::defaultMonths(),
                'until' => $sub?->until?->format('Y-m-d'),
                'auto_pay' => (bool) $sub?->auto_pay,
                'has_terms' => $sub !== null,
                'card' => $user->card_last4 ? trim(($user->card_brand ?? '').' •••• '.$user->card_last4) : null,
            ],
            'invoices' => Invoice::where('user_id', $user->id)->latest()->limit(10)->get()->map(fn (Invoice $i) => [
                'id' => $i->id,
                'amount' => $i->amount,
                'months' => $i->months,
                'status' => $i->status,
                'date' => $i->created_at?->format('d.m.Y'),
                'confirmation_url' => $i->confirmation_url,
            ])->values(),
        ];
    }
}
