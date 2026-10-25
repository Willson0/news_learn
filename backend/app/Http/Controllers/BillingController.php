<?php

namespace App\Http\Controllers;

use App\Models\Invoice;
use App\Models\User;
use App\Services\InvoiceService;
use App\Services\SubscriptionBilling;
use App\Services\TbankClient;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;
use RuntimeException;
use Symfony\Component\HttpFoundation\Response;

/**
 * Привязка банковской карты через Т-Кассу. Платёж на сумму TBANK_BIND_AMOUNT с признаком Recurrent
 * сохраняет карту (RebillId) для автопродления; после этого сумма возвращается.
 */
class BillingController extends Controller
{
    public function __construct(
        private TbankClient $tbank,
        private SubscriptionBilling $billing,
        private InvoiceService $invoices,
    ) {}

    /**
     * Приложение открывает этот запрос при возврате из Т-Кассы. Уведомление может прийти чуть позже,
     * поэтому, если привязка ещё ждёт результата, сами запрашиваем состояние платежа.
     */
    public function card(Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $request->user();

        $this->settlePendingBind($user);

        return response()->json($this->cardPayload($user->refresh()));
    }

    public function bind(Request $request): JsonResponse
    {
        /** @var User $user */
        $user = $request->user();

        if (! $this->tbank->configured()) {
            Log::error('Т-Касса: не заданы TBANK_TERMINAL_KEY / TBANK_PASSWORD');

            return response()->json(['message' => 'Привязка карты временно недоступна'], 503);
        }

        try {
            $payment = $this->tbank->init(
                amount: (int) config('services.tbank.bind_amount'),
                orderId: 'bind-'.$user->id.'-'.Str::random(8),
                description: 'Привязка карты',
                customerKey: TbankClient::customerKey($user->id),
                recurrent: true,
                email: $user->email,
            );
        } catch (RuntimeException $e) {
            Log::error('Т-Касса: не создан платёж привязки карты', ['user_id' => $user->id, 'error' => $e->getMessage()]);

            return response()->json(['message' => 'Не удалось начать привязку карты'], 502);
        }

        $paymentUrl = $payment['PaymentURL'] ?? null;
        if (! is_string($paymentUrl) || $paymentUrl === '') {
            Log::error('Т-Касса: в ответе нет PaymentURL', ['user_id' => $user->id, 'payment' => $payment]);

            return response()->json(['message' => 'Не удалось начать привязку карты'], 502);
        }

        // Запоминаем платёж привязки, чтобы при возврате из Т-Кассы дождаться его результата.
        Cache::put($this->pendingKey($user), (string) $payment['PaymentId'], now()->addDay());

        // Имя поля сохранено для приложений: там это ссылка на страницу оплаты.
        return response()->json(['confirmation_url' => $paymentUrl]);
    }

    /**
     * Возврат после страницы Т-Кассы. Сам факт привязки подтверждает уведомление,
     * поэтому здесь только передаём статус в приложение.
     */
    public function return(Request $request): RedirectResponse
    {
        $status = $request->query('status') === 'cancel' ? 'error' : 'ok';

        return redirect(config('services.tbank.app_scheme').'://billing?status='.$status);
    }

    public function unbind(Request $request): Response
    {
        /** @var User $user */
        $user = $request->user();

        // Новые списания по RebillId мы больше не делаем; карта удаляется у нас.
        $user->forceFill([
            'tbank_rebill_id' => null,
            'card_brand' => null,
            'card_last4' => null,
            'card_bound_at' => null,
        ])->save();

        return response()->noContent();
    }

    /**
     * Уведомление Т-Кассы о смене статуса платежа. Подпись Token обязательна. Состояние платежа
     * заново запрашиваем у API (GetState), и по нему решаем, что делать. Повторные уведомления безопасны.
     */
    public function webhook(Request $request): Response
    {
        $payload = $request->json()->all() ?: $request->all();

        if (! $this->tbank->verify($payload)) {
            Log::warning('Т-Касса: уведомление с неверной подписью', ['payment_id' => $payload['PaymentId'] ?? null]);

            return response('Forbidden', 403);
        }

        $paymentId = (string) ($payload['PaymentId'] ?? '');
        if ($paymentId !== '') {
            $this->settle($paymentId, $payload);
        }

        // Т-Касса повторяет уведомление, пока не получит ответ OK.
        return response('OK', 200);
    }

    /**
     * Применяет актуальное состояние платежа в зависимости от назначения, которое закодировано в OrderId.
     * Статус берём из GetState, а данные сохранённой карты (RebillId, Pan) — из подписанного уведомления:
     * Т-Касса передаёт их именно в уведомлении.
     *
     * @param  array<string, mixed>  $notification
     */
    private function settle(string $paymentId, array $notification): void
    {
        try {
            $state = $this->tbank->getState($paymentId);
        } catch (RuntimeException $e) {
            Log::error('Т-Касса: не удалось получить состояние платежа', ['payment_id' => $paymentId, 'error' => $e->getMessage()]);

            return;
        }

        // Уведомление и GetState должны относиться к одному платежу.
        if ((string) ($state['PaymentId'] ?? $paymentId) !== $paymentId) {
            return;
        }
        $state['PaymentId'] = $paymentId;
        foreach (['RebillId', 'Pan', 'CardId', 'OrderId'] as $field) {
            if (empty($state[$field]) && isset($notification[$field]) && is_scalar($notification[$field])) {
                $state[$field] = (string) $notification[$field];
            }
        }

        if (! preg_match('/^(inv|bind|renew)-(\d+)-/', (string) ($state['OrderId'] ?? ''), $m)) {
            return;
        }
        [, $purpose, $id] = $m;

        // Счёт, выставленный админом: успешная оплата активирует подписку по условиям счёта.
        if ($purpose === 'inv') {
            $invoice = Invoice::with('user')->find((int) $id);
            if ($invoice && $invoice->status === Invoice::PENDING) {
                // Оплачена должна быть ровно сумма счёта (в копейках).
                $paidAmount = isset($state['Amount']) ? (int) $state['Amount'] : null;
                if ($paidAmount !== null && $paidAmount !== $invoice->amount * 100) {
                    Log::warning('Т-Касса: сумма оплаты не совпадает со счётом', ['invoice_id' => $invoice->id, 'amount' => $paidAmount]);

                    return;
                }
                if (($state['Status'] ?? null) === TbankClient::SUCCESS_STATUS) {
                    $this->invoices->markPaid($invoice, $state);
                } elseif (in_array($state['Status'] ?? null, TbankClient::FAILED_STATUSES, true)) {
                    $invoice->update(['status' => Invoice::CANCELED]);
                }
            }

            return;
        }

        $user = User::find((int) $id);
        if (! $user) {
            return;
        }

        if ($purpose === 'bind') {
            if (($state['Status'] ?? null) === TbankClient::SUCCESS_STATUS) {
                $this->applyCardBind($user, $state);
            }

            return;
        }

        // Списание подписки: успех продлевает доступ, отказ (например, нет денег) — отключает.
        $this->billing->apply($user, $state);
    }

    /**
     * Если привязка карты ждёт результата, запрашиваем состояние платежа и применяем его.
     */
    private function settlePendingBind(User $user): void
    {
        $key = $this->pendingKey($user);
        $paymentId = (string) Cache::get($key, '');
        if ($paymentId === '' || ! $this->tbank->configured()) {
            return;
        }

        try {
            $state = $this->tbank->getState($paymentId);
        } catch (RuntimeException $e) {
            Log::error('Т-Касса: не удалось проверить привязку карты', ['user_id' => $user->id, 'payment_id' => $paymentId, 'error' => $e->getMessage()]);

            return;
        }

        $status = $state['Status'] ?? null;
        $state['PaymentId'] = $paymentId;
        if ($status === TbankClient::SUCCESS_STATUS) {
            // Без RebillId в ответе GetState ждём уведомление: карту сохранит вебхук.
            if ($this->applyCardBind($user, $state, quiet: true)) {
                Cache::forget($key);
            }
        } elseif (in_array($status, TbankClient::FAILED_STATUSES, true)) {
            Cache::forget($key);
        }
    }

    /**
     * Сохраняет RebillId из успешного платежа привязки, возвращает сумму привязки
     * и списывает подписку сразу, если её ещё нет или она отключена.
     *
     * @param  array<string, mixed>  $state
     * @return bool  карта сохранена (сейчас или раньше)
     */
    private function applyCardBind(User $user, array $state, bool $quiet = false): bool
    {
        $rebillId = isset($state['RebillId']) && is_scalar($state['RebillId']) ? (string) $state['RebillId'] : '';
        if ($rebillId === '') {
            if (! $quiet) {
                Log::warning('Т-Касса: платёж привязки без RebillId', ['user_id' => $user->id, 'payment_id' => $state['PaymentId'] ?? null]);
            }

            return false;
        }

        // Уже привязана эта карта: повторное уведомление ничего не меняет.
        if ($user->tbank_rebill_id === $rebillId) {
            return true;
        }

        $user->forceFill([
            'tbank_rebill_id' => $rebillId,
            'card_brand' => null,
            'card_last4' => TbankClient::cardLast4($state),
            'card_bound_at' => now(),
        ])->save();

        $paymentId = (string) $state['PaymentId'];
        try {
            $this->tbank->cancel($paymentId);
        } catch (RuntimeException $e) {
            Log::warning('Т-Касса: не удалось вернуть сумму привязки', ['payment_id' => $paymentId, 'error' => $e->getMessage()]);
        }

        $subscription = $user->subscription;
        if (! $subscription || ! $subscription->active) {
            try {
                $this->billing->apply($user, $this->billing->charge($user, 'renew-'.$user->id.'-bind-'.Str::random(6)));
            } catch (RuntimeException $e) {
                Log::warning('Подписка не списана после привязки карты', ['user_id' => $user->id, 'error' => $e->getMessage()]);
            }
        }

        return true;
    }

    private function pendingKey(User $user): string
    {
        return 'tbank:card_bind:'.$user->id;
    }

    /** @return array{bound: bool, brand: ?string, last4: ?string} */
    private function cardPayload(User $user): array
    {
        $bound = $user->tbank_rebill_id !== null;

        return [
            'bound' => $bound,
            'brand' => $bound ? $user->card_brand : null,
            'last4' => $bound ? $user->card_last4 : null,
        ];
    }
}
