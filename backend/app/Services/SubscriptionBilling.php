<?php

namespace App\Services;

use App\Models\Subscription;
use App\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Log;

/**
 * Списание подписки с сохранённой карты YooKassa и продление/отключение доступа.
 * Списание идёт без участия пользователя (recurring по payment_method_id).
 */
class SubscriptionBilling
{
    public const PURPOSE = 'subscription';

    public function __construct(private YookassaClient $yookassa)
    {
    }

    /** Есть ли у пользователя сохранённая карта и настроено ли списание. */
    public function canCharge(User $user): bool
    {
        return $user->yookassa_payment_method_id !== null && $this->yookassa->configured();
    }

    /**
     * Создаёт платёж за период. Ключ идемпотентности не даёт списать дважды за один период.
     * Возвращает платёж YooKassa (статус может быть pending — тогда решает вебхук).
     *
     * @return array<string, mixed>
     */
    public function charge(User $user, string $idempotenceKey): array
    {
        return $this->yookassa->createPayment([
            'amount' => [
                'value' => (string) config('services.yookassa.subscription_amount'),
                'currency' => 'RUB',
            ],
            'capture' => true,
            'payment_method_id' => $user->yookassa_payment_method_id,
            'description' => 'Подписка «Карта ликвидности»',
            'metadata' => [
                'purpose' => self::PURPOSE,
                'user_id' => (string) $user->id,
            ],
        ], $idempotenceKey);
    }

    /** Успешная оплата: подписка активна ещё на один период (от текущего окончания или от сегодня). */
    public function markPaid(User $user, string $paymentId): void
    {
        $sub = Subscription::firstOrCreate(['user_id' => $user->id]);
        if ($sub->last_payment_id === $paymentId) {
            return;
        }

        $months = (int) config('services.yookassa.subscription_months', 1);
        $base = $sub->until && $sub->until->gte(today()) ? $sub->until : today();

        $sub->fill([
            'active' => true,
            'plan' => $sub->plan ?? 'monthly',
            'until' => Carbon::parse($base)->addMonths($months)->toDateString(),
            'auto_pay' => true,
            'payment_method' => 'card',
            'last_payment_id' => $paymentId,
        ])->save();
    }

    /** Списание не прошло (например, не хватает денег): доступ по подписке отключаем. */
    public function markFailed(User $user): void
    {
        Subscription::where('user_id', $user->id)->update(['active' => false]);
        Log::info('Подписка отключена: списание не прошло', ['user_id' => $user->id]);
    }

    /** Продлевает подписки, срок которых вышел. Вызывается ежедневно планировщиком. */
    public function renewDue(): int
    {
        $count = 0;
        Subscription::with('user')
            ->where('auto_pay', true)
            ->whereDate('until', '<=', today())
            ->each(function (Subscription $sub) use (&$count) {
                $user = $sub->user;
                if (! $user) {
                    return;
                }
                if (! $this->canCharge($user)) {
                    $this->markFailed($user);

                    return;
                }

                try {
                    $payment = $this->charge($user, 'renew-'.$sub->id.'-'.today()->format('Ymd'));
                } catch (\Throwable $e) {
                    // Сбой сети/API не отключает подписку: попробуем в следующий запуск.
                    Log::warning('Продление подписки не отправлено', ['user_id' => $user->id, 'error' => $e->getMessage()]);

                    return;
                }

                $status = $payment['status'] ?? null;
                if ($status === 'succeeded') {
                    $this->markPaid($user, (string) $payment['id']);
                } elseif ($status === 'canceled') {
                    $this->markFailed($user);
                }
                $count++;
            });

        return $count;
    }
}
