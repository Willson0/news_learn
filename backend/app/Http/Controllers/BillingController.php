<?php

namespace App\Http\Controllers;

use App\Models\Invoice;
use App\Models\User;
use App\Services\InvoiceService;
use App\Services\SubscriptionBilling;
use App\Services\YookassaClient;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Symfony\Component\HttpFoundation\Response;

/**
 * Привязка банковской карты через YooKassa. Списания не делаем: платёж на сумму
 * YOOKASSA_BIND_AMOUNT нужен только для сохранения способа оплаты и сразу возвращается.
 */
class BillingController extends Controller
{
    public const PURPOSE = 'card_bind';

    public function __construct(
        private YookassaClient $yookassa,
        private SubscriptionBilling $billing,
        private InvoiceService $invoices,
    ) {}

    public function card(Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $request->user();

        return response()->json($this->cardPayload($user));
    }

    public function bind(Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $request->user();

        if (! $this->yookassa->configured()) {
            Log::error('YooKassa: не заданы YOOKASSA_SHOP_ID / YOOKASSA_SECRET_KEY');

            return response()->json(['message' => 'Привязка карты временно недоступна'], 503);
        }

        $payment = $this->yookassa->createPayment([
            'amount' => [
                'value' => (string) config('services.yookassa.bind_amount'),
                'currency' => 'RUB',
            ],
            'capture' => true,
            'save_payment_method' => true,
            'confirmation' => [
                'type' => 'redirect',
                'return_url' => (string) config('services.yookassa.return_url'),
            ],
            'description' => 'Привязка карты',
            'metadata' => [
                'purpose' => self::PURPOSE,
                'user_id' => (string) $user->id,
            ],
        ]);

        $confirmationUrl = $payment['confirmation']['confirmation_url'] ?? null;
        if (! is_string($confirmationUrl) || $confirmationUrl === '') {
            return response()->json(['message' => 'Не удалось начать привязку карты'], 502);
        }

        return response()->json(['confirmation_url' => $confirmationUrl]);
    }

    /**
     * Возврат после страницы YooKassa. Сам факт привязки подтверждает вебхук,
     * поэтому здесь только передаём статус в приложение.
     */
    public function return(Request $request): RedirectResponse
    {
        $status = $request->query('status') === 'cancel' ? 'error' : 'ok';

        return redirect(config('services.yookassa.app_scheme').'://billing?status='.$status);
    }

    public function unbind(Request $request): Response
    {
        /** @var User $user */
        $user = $request->user();

        // У YooKassa нет публичного метода удаления сохранённого способа оплаты в API v3,
        // поэтому отвязываем локально: новые списания по этому id мы не делаем.
        $user->forceFill([
            'yookassa_payment_method_id' => null,
            'card_brand' => null,
            'card_last4' => null,
            'card_bound_at' => null,
        ])->save();

        return response()->noContent();
    }

    /**
     * Уведомление YooKassa. Событие не доверяем: платёж заново запрашиваем у API
     * магазина и по нему решаем, что привязывать. Повторные уведомления безопасны.
     */
    public function webhook(Request $request): Response
    {
        $event = (string) $request->input('event');
        $paymentId = (string) $request->input('object.id');
        if (! in_array($event, ['payment.succeeded', 'payment.canceled'], true) || $paymentId === '') {
            return response()->noContent();
        }

        $payment = $this->yookassa->fetchPayment($paymentId);
        $purpose = $payment['metadata']['purpose'] ?? null;

        // Счёт, выставленный админом: успешная оплата активирует подписку по условиям счёта.
        if ($purpose === InvoiceService::PURPOSE) {
            $invoice = Invoice::with('user')->find((int) ($payment['metadata']['invoice_id'] ?? 0));
            if ($invoice && $invoice->status === Invoice::PENDING) {
                if (($payment['status'] ?? null) === 'succeeded') {
                    $this->invoices->markPaid($invoice, $payment);
                } elseif (($payment['status'] ?? null) === 'canceled') {
                    $invoice->update(['status' => Invoice::CANCELED]);
                }
            }

            return response()->noContent();
        }

        // Списание подписки: успех продлевает доступ, отказ (например, нет денег) — отключает.
        if ($purpose === SubscriptionBilling::PURPOSE) {
            $user = User::find((int) ($payment['metadata']['user_id'] ?? 0));
            if ($user) {
                if (($payment['status'] ?? null) === 'succeeded') {
                    $this->billing->markPaid($user, $paymentId);
                } elseif (($payment['status'] ?? null) === 'canceled') {
                    $this->billing->markFailed($user);
                }
            }

            return response()->noContent();
        }

        if ($event !== 'payment.succeeded'
            || ($payment['status'] ?? null) !== 'succeeded'
            || $purpose !== self::PURPOSE) {
            return response()->noContent();
        }

        $user = User::find((int) ($payment['metadata']['user_id'] ?? 0));
        $method = $payment['payment_method'] ?? [];
        $methodId = $method['id'] ?? null;
        if (! $user || ! is_string($methodId) || $methodId === '' || ! ($method['saved'] ?? false)) {
            return response()->noContent();
        }

        // Уже привязан этот способ оплаты: повторное уведомление ничего не меняет.
        if ($user->yookassa_payment_method_id === $methodId) {
            return response()->noContent();
        }

        $user->forceFill([
            'yookassa_payment_method_id' => $methodId,
            'card_brand' => $method['card']['card_type'] ?? null,
            'card_last4' => $method['card']['last4'] ?? null,
            'card_bound_at' => now(),
        ])->save();

        $this->refundBindAmount($paymentId, $payment);

        // Подписки ещё нет (или она отключена) — списываем её сразу с привязанной карты.
        $subscription = $user->subscription;
        if (! $subscription || ! $subscription->active) {
            try {
                $this->billing->charge($user, 'subscription-after-bind-'.$paymentId);
            } catch (\Throwable $e) {
                Log::warning('Подписка не списана после привязки карты', ['user_id' => $user->id, 'error' => $e->getMessage()]);
            }
        }

        return response()->noContent();
    }

    /**
     * Сумма за привязку возвращается пользователю. Ключ идемпотентности от id платежа:
     * YooKassa не создаст второй возврат, даже если вебхук придёт повторно.
     */
    private function refundBindAmount(string $paymentId, array $payment): void
    {
        try {
            $this->yookassa->createRefund([
                'payment_id' => $paymentId,
                'amount' => $payment['amount'],
                'description' => 'Возврат за привязку карты',
            ], 'refund-bind-'.$paymentId);
        } catch (\Throwable $e) {
            Log::warning('YooKassa: не удалось вернуть сумму привязки', [
                'payment_id' => $paymentId,
                'error' => $e->getMessage(),
            ]);
        }
    }

    private function cardPayload(User $user): array
    {
        $bound = $user->yookassa_payment_method_id !== null;

        return [
            'bound' => $bound,
            'brand' => $bound ? $user->card_brand : null,
            'last4' => $bound ? $user->card_last4 : null,
        ];
    }
}
