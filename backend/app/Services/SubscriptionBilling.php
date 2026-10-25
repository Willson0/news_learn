<?php

namespace App\Services;

use App\Models\AppSetting;
use App\Models\Payment;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Log;

/**
 * Списание подписки с сохранённой карты Т-Кассы и продление/отключение доступа.
 * Списание идёт без участия пользователя: Init создаёт платёж, Charge списывает по RebillId.
 * Сумма и срок берутся из индивидуальных условий подписки, а если их нет — из настроек.
 */
class SubscriptionBilling
{
    public function __construct(private TbankClient $tbank) {}

    /** Есть ли у пользователя сохранённая карта и настроено ли списание. */
    public function canCharge(User $user): bool
    {
        return $user->tbank_rebill_id !== null && $this->tbank->configured();
    }

    /**
     * Списывает период. Номер заказа (OrderId) уникален: повторная попытка с тем же номером
     * за тот же день отклоняется Т-Кассой, поэтому второго списания не будет.
     *
     * @return array<string, mixed>  ответ Charge; статус CONFIRMED — оплачено, REJECTED — отказ
     */
    public function charge(User $user, string $orderId): array
    {
        [$amount] = $this->terms($user);

        $init = $this->tbank->init(
            amount: $amount,
            orderId: $orderId,
            description: 'Подписка «Карта ликвидности»',
            customerKey: TbankClient::customerKey($user->id),
        );

        $charge = $this->tbank->charge((string) $init['PaymentId'], (string) $user->tbank_rebill_id);

        return ['PaymentId' => $init['PaymentId']] + $charge;
    }

    /**
     * Применяет результат списания: успех продлевает доступ, отказ отключает его.
     * Промежуточные статусы ничего не меняют, решает уведомление Т-Кассы.
     *
     * @param  array<string, mixed>  $payment
     */
    public function apply(User $user, array $payment): void
    {
        $status = $payment['Status'] ?? null;
        if ($status === TbankClient::SUCCESS_STATUS) {
            $this->markPaid($user, (string) $payment['PaymentId']);
        } elseif (in_array($status, TbankClient::FAILED_STATUSES, true)) {
            $this->markFailed($user);
        }
    }

    /**
     * Успешная оплата: подписка активна ещё на один период (от текущего окончания или от сегодня).
     */
    public function markPaid(User $user, string $paymentId): void
    {
        $sub = Subscription::firstOrCreate(['user_id' => $user->id]);
        if ($sub->last_payment_id === $paymentId) {
            return;
        }

        [$amount, $months] = $this->terms($user);
        $base = $sub->until && $sub->until->gte(today()) ? $sub->until : today();

        $sub->fill([
            'active' => true,
            'plan' => $sub->plan ?? 'monthly',
            'until' => Carbon::parse($base)->addMonths($months)->toDateString(),
            'auto_pay' => true,
            'payment_method' => 'card',
            'last_payment_id' => $paymentId,
        ])->save();

        Payment::firstOrCreate(['tbank_payment_id' => $paymentId], [
            'user_id' => $user->id,
            'kind' => 'renewal',
            'amount' => $amount,
            'months' => $months,
            'paid_at' => now(),
        ]);
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
                    $payment = $this->charge($user, 'renew-'.$user->id.'-'.today()->format('Ymd'));
                } catch (\Throwable $e) {
                    // Сбой сети/API не отключает подписку: попробуем в следующий запуск.
                    Log::warning('Продление подписки не отправлено', ['user_id' => $user->id, 'error' => $e->getMessage()]);

                    return;
                }

                $this->apply($user, $payment);
                $count++;
            });

        return $count;
    }

    /**
     * Сумма и срок периода для пользователя: индивидуальные условия или значения по умолчанию.
     *
     * @return array{0: int, 1: int}
     */
    public function terms(User $user): array
    {
        $sub = $user->subscription;

        return [
            $sub?->amount ?? AppSetting::defaultPrice(),
            $sub?->months ?? AppSetting::defaultMonths(),
        ];
    }
}
