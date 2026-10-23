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
 * Счета, которые выставляет админ: оплата через YooKassa, активация подписки и push-уведомления.
 */
class InvoiceService
{
    public const PURPOSE = 'invoice';

    public function __construct(
        private YookassaClient $yookassa,
        private PushService $push,
    ) {}

    /**
     * Выставляет счёт пользователю: создаёт платёж YooKassa с сохранением карты
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

        $payment = $this->yookassa->createPayment([
            'amount' => ['value' => number_format($amount, 2, '.', ''), 'currency' => 'RUB'],
            'capture' => true,
            'save_payment_method' => true,
            'confirmation' => [
                'type' => 'redirect',
                'return_url' => (string) config('services.yookassa.return_url'),
            ],
            'description' => "Подписка «Карта ликвидности», {$months} мес.",
            'metadata' => [
                'purpose' => self::PURPOSE,
                'invoice_id' => (string) $invoice->id,
                'user_id' => (string) $user->id,
            ],
        ], 'invoice-'.$invoice->id.'-'.Str::random(8));

        $invoice->update([
            'yookassa_payment_id' => $payment['id'] ?? null,
            'confirmation_url' => $payment['confirmation']['confirmation_url'] ?? null,
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
     * Оплата счёта прошла: счёт закрыт, подписка продлена на срок счёта, способ оплаты сохранён.
     *
     * @param  array<string, mixed>  $payment  платёж YooKassa
     */
    public function markPaid(Invoice $invoice, array $payment): void
    {
        if ($invoice->status === Invoice::PAID) {
            return;
        }

        DB::transaction(function () use ($invoice, $payment) {
            $user = $invoice->user;
            $sub = Subscription::firstOrCreate(['user_id' => $user->id]);
            $base = $sub->until && $sub->until->gte(today()) ? $sub->until : today();

            $sub->fill([
                'active' => true,
                'plan' => 'custom',
                'amount' => $invoice->amount,
                'months' => $invoice->months,
                'until' => Carbon::parse($base)->addMonths($invoice->months)->toDateString(),
                'auto_pay' => true,
                'payment_method' => 'card',
                'last_payment_id' => (string) $payment['id'],
            ])->save();

            $invoice->update(['status' => Invoice::PAID, 'paid_at' => now()]);

            Payment::firstOrCreate(['yookassa_payment_id' => (string) $payment['id']], [
                'user_id' => $user->id,
                'invoice_id' => $invoice->id,
                'kind' => 'invoice',
                'amount' => $invoice->amount,
                'months' => $invoice->months,
                'paid_at' => now(),
            ]);

            // Сохранённая карта нужна для автопродления по этим же условиям.
            $method = $payment['payment_method'] ?? [];
            if (($method['saved'] ?? false) && is_string($method['id'] ?? null)) {
                $user->forceFill([
                    'yookassa_payment_method_id' => $method['id'],
                    'card_brand' => $method['card']['card_type'] ?? null,
                    'card_last4' => $method['card']['last4'] ?? null,
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
