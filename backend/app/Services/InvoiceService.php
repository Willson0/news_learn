<?php

namespace App\Services;

use App\Models\AppSetting;
use App\Models\Invoice;
use App\Models\Payment;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Счета, которые выставляет админ: оплата через Т-Кассу, активация подписки и push-уведомления.
 */
class InvoiceService
{
    public const PURPOSE = 'invoice';

    public function __construct(
        private TbankClient $tbank,
        private PushService $push,
    ) {}

    /**
     * Выставляет счёт пользователю: создаёт платёж Т-Кассы с сохранением карты (Recurrent)
     * и отправляет пользователю ссылку на оплату push-уведомлением.
     */
    public function issue(User $user, int $amount, int $months, ?string $createdBy): Invoice
    {
        $invoice = Invoice::create([
            'user_id' => $user->id,
            'amount' => $amount,
            'months' => $months,
            'status' => Invoice::PENDING,
            'created_by' => $createdBy,
        ]);

        try {
            $payment = $this->tbank->init(
                amount: $amount,
                orderId: 'inv-'.$invoice->id.'-'.Str::random(8),
                description: "Подписка «Карта ликвидности», {$months} мес.",
                customerKey: TbankClient::customerKey($user->id),
                recurrent: true,
                email: $user->email,
            );
        } catch (\Throwable $e) {
            // Платёж в Т-Кассе не создан: пустой счёт не оставляем.
            $invoice->delete();

            throw $e;
        }

        $invoice->update([
            'tbank_payment_id' => $payment['PaymentId'] ?? null,
            'confirmation_url' => $payment['PaymentURL'] ?? null,
        ]);

        $this->push->sendToUser(
            $user,
            'Выставлен счёт',
            "Подписка: {$this->money($amount)} за {$this->period($months)}. Откройте ссылку для оплаты.",
            ['type' => 'invoice', 'url' => (string) $invoice->confirmation_url],
        );

        return $invoice->refresh();
    }

    /**
     * Бесплатный доступ до указанной даты: без счёта и без автопродления. Пользователю уходит push.
     */
    public function grantFree(User $user, Carbon $until): Subscription
    {
        $sub = Subscription::firstOrCreate(['user_id' => $user->id]);
        $sub->fill([
            'active' => true,
            'plan' => 'gift',
            'until' => $until->toDateString(),
            'auto_pay' => false,
        ])->save();

        $this->push->sendToUser(
            $user,
            'Подписка открыта',
            'Вам открыли доступ к подписке до '.$until->format('d.m.Y').'.',
            ['type' => 'subscription'],
        );

        return $sub;
    }

    /**
     * Меняет условия подписки пользователя (сумма, срок, автопродление). Пользователю уходит push.
     *
     * @param  array{amount?: int, months?: int, until?: string|null, auto_pay?: bool, active?: bool}  $terms
     */
    public function changeTerms(User $user, array $terms): Subscription
    {
        $sub = Subscription::firstOrCreate(['user_id' => $user->id]);
        $sub->fill(array_filter([
            'amount' => $terms['amount'] ?? null,
            'months' => $terms['months'] ?? null,
            'plan' => isset($terms['amount']) ? 'custom' : null,
        ], fn ($v) => $v !== null));

        if (array_key_exists('until', $terms)) {
            $sub->until = $terms['until'] ? Carbon::parse($terms['until']) : null;
        }
        if (array_key_exists('active', $terms)) {
            $sub->active = (bool) $terms['active'];
        }
        if (array_key_exists('auto_pay', $terms)) {
            $sub->auto_pay = (bool) $terms['auto_pay'];
        }
        $sub->save();

        $this->push->sendToUser(
            $user,
            'Условия подписки изменены',
            "Теперь подписка: {$this->money($sub->amount ?? AppSetting::defaultPrice())} за {$this->period($sub->months ?? AppSetting::defaultMonths())}.",
            ['type' => 'subscription'],
        );

        return $sub;
    }

    /**
     * Оплата счёта прошла: счёт закрыт, подписка продлена на срок счёта, карта сохранена для автопродления.
     *
     * @param  array<string, mixed>  $state  состояние платежа из уведомления или GetState Т-Кассы
     */
    public function markPaid(Invoice $invoice, array $state): void
    {
        if ($invoice->status === Invoice::PAID) {
            return;
        }

        DB::transaction(function () use ($invoice, $state) {
            $user = $invoice->user;
            $sub = Subscription::firstOrCreate(['user_id' => $user->id]);
            $base = $sub->until && $sub->until->toDateString() >= Subscription::today() ? $sub->until : Subscription::today();

            $sub->fill([
                'active' => true,
                'plan' => 'custom',
                'amount' => $invoice->amount,
                'months' => $invoice->months,
                'until' => Carbon::parse($base)->addMonths($invoice->months)->toDateString(),
                'auto_pay' => true,
                'payment_method' => 'card',
                'last_payment_id' => (string) $state['PaymentId'],
            ])->save();

            $invoice->update(['status' => Invoice::PAID, 'paid_at' => now()]);

            Payment::firstOrCreate(['tbank_payment_id' => (string) $state['PaymentId']], [
                'user_id' => $user->id,
                'invoice_id' => $invoice->id,
                'kind' => 'invoice',
                'amount' => $invoice->amount,
                'months' => $invoice->months,
                'paid_at' => now(),
            ]);

            // Карта, сохранённая этим платежом (RebillId), нужна для автопродления по этим же условиям.
            $rebillId = isset($state['RebillId']) && is_scalar($state['RebillId']) ? (string) $state['RebillId'] : '';
            if ($rebillId !== '') {
                $user->forceFill([
                    'tbank_rebill_id' => $rebillId,
                    'card_brand' => null,
                    'card_last4' => TbankClient::cardLast4($state),
                    'card_bound_at' => now(),
                ])->save();
            }
        });

        $this->push->sendToUser(
            $invoice->user,
            'Оплата получена',
            'Подписка активна до '.$invoice->user->subscription->until->format('d.m.Y').'.',
            ['type' => 'subscription'],
        );
    }

    private function money(int $amount): string
    {
        return number_format($amount, 0, ',', ' ').' ₽';
    }

    private function period(int $months): string
    {
        return match (true) {
            $months === 1 => '1 месяц',
            $months >= 2 && $months <= 4 => "{$months} месяца",
            default => "{$months} месяцев",
        };
    }
}
