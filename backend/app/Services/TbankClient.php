<?php

namespace App\Services;

use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Http;
use RuntimeException;

/**
 * Тонкий клиент API Т-Кассы (бывш. Тинькофф Касса), версия v2.
 * Каждый запрос подписывается полем Token: SHA-256 от значений параметров, отсортированных по имени,
 * с добавленным паролем терминала. Суммы передаём в рублях, в запрос уходят копейки.
 */
class TbankClient
{
    /** Статусы платежа, после которых деньги не спишутся: платёж окончен без успеха. */
    public const FAILED_STATUSES = ['REJECTED', 'CANCELED', 'DEADLINE_EXPIRED', 'AUTH_FAIL'];

    public const SUCCESS_STATUS = 'CONFIRMED';

    public function configured(): bool
    {
        return (string) config('services.tbank.terminal_key') !== ''
            && (string) config('services.tbank.password') !== '';
    }

    /**
     * Инициирует платёж и возвращает страницу оплаты. С $recurrent платёж сохранит карту
     * и вернёт RebillId, по которому потом списываются регулярные платежи.
     *
     * @return array<string, mixed>
     */
    public function init(int $amount, string $orderId, string $description, ?string $customerKey = null, bool $recurrent = false, ?string $successUrl = null): array
    {
        $params = [
            'Amount' => $this->kopecks($amount),
            'OrderId' => $orderId,
            'Description' => $description,
            'NotificationURL' => (string) config('services.tbank.notification_url'),
            'SuccessURL' => $successUrl ?? (string) config('services.tbank.return_url'),
            'FailURL' => (string) config('services.tbank.fail_url'),
        ];
        if ($customerKey !== null) {
            $params['CustomerKey'] = $customerKey;
        }
        if ($recurrent) {
            $params['Recurrent'] = 'Y';
        }

        return $this->send('Init', $params);
    }

    /**
     * Списание по сохранённому RebillId. Сначала создаётся платёж через init(), затем Charge.
     *
     * @return array<string, mixed>
     */
    public function charge(string $paymentId, string $rebillId): array
    {
        return $this->send('Charge', ['PaymentId' => $paymentId, 'RebillId' => $rebillId]);
    }

    /** @return array<string, mixed> */
    public function getState(string $paymentId): array
    {
        return $this->send('GetState', ['PaymentId' => $paymentId]);
    }

    /** Возврат. Без суммы возвращается весь платёж. */
    public function cancel(string $paymentId, ?int $amount = null): array
    {
        $params = ['PaymentId' => $paymentId];
        if ($amount !== null) {
            $params['Amount'] = $this->kopecks($amount);
        }

        return $this->send('Cancel', $params);
    }

    /**
     * Проверяет подпись уведомления. Без корректной подписи уведомление не обрабатываем.
     *
     * @param  array<string, mixed>  $payload
     */
    public function verify(array $payload): bool
    {
        $token = $payload['Token'] ?? null;
        if (! is_string($token) || $token === '' || (string) config('services.tbank.password') === '') {
            return false;
        }

        return hash_equals($this->token($payload), $token);
    }

    /**
     * Значение поля Token: параметры верхнего уровня без Token и вложенных объектов,
     * плюс Password; ключи сортируются по имени; значения склеиваются; результат — SHA-256 в hex.
     *
     * @param  array<string, mixed>  $params
     */
    public function token(array $params): string
    {
        $params = Arr::except($params, ['Token', 'Receipt', 'DATA', 'Shops']);
        $params = array_filter($params, fn ($value) => ! is_array($value));
        $params['Password'] = (string) config('services.tbank.password');
        ksort($params, SORT_STRING);

        $concatenated = '';
        foreach ($params as $value) {
            $concatenated .= is_bool($value) ? ($value ? 'true' : 'false') : (string) $value;
        }

        return hash('sha256', $concatenated);
    }

    /**
     * @param  array<string, mixed>  $params
     * @return array<string, mixed>
     */
    private function send(string $method, array $params): array
    {
        $terminalKey = (string) config('services.tbank.terminal_key');
        if (! $this->configured()) {
            throw new RuntimeException('Т-Касса не настроена: задайте TBANK_TERMINAL_KEY и TBANK_PASSWORD.');
        }

        $body = ['TerminalKey' => $terminalKey] + $params;
        $body['Token'] = $this->token($body);

        /** @var PendingRequest $http */
        $http = Http::baseUrl((string) config('services.tbank.api_base'))
            ->acceptJson()
            ->timeout(15);

        $response = $http->post($method, $body);

        $json = $response->json() ?? [];
        // Отказ по платежу приходит со статусом REJECTED, это не ошибка запроса. Ошибкой считаем только отказ API.
        $apiError = ($json['ErrorCode'] ?? '0') !== '0' && ! isset($json['Status']);
        if (! $response->successful() || $apiError) {
            throw new RuntimeException("Т-Касса, {$method}: ".($json['Message'] ?? '').' '.($json['Details'] ?? $response->body()));
        }

        return $json;
    }

    /** Ключ покупателя в Т-Кассе: по нему привязываются карты и идут регулярные платежи. */
    public static function customerKey(int $userId): string
    {
        return 'user-'.$userId;
    }

    /** Последние четыре цифры карты из маски Pan (например, 430000******0777). */
    public static function cardLast4(array $state): ?string
    {
        $pan = $state['Pan'] ?? null;

        return is_string($pan) && strlen($pan) >= 4 ? substr($pan, -4) : null;
    }

    private function kopecks(int $rubles): int
    {
        return $rubles * 100;
    }
}
