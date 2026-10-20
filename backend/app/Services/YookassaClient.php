<?php

namespace App\Services;

use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;
use RuntimeException;

/**
 * Тонкий клиент API YooKassa (v3): авторизация Basic shop_id:secret_key,
 * Idempotence-Key на каждом изменяющем запросе.
 */
class YookassaClient
{
    public function configured(): bool
    {
        return (string) config('services.yookassa.shop_id') !== ''
            && (string) config('services.yookassa.secret_key') !== '';
    }

    public function createPayment(array $payload): array
    {
        return $this->send('post', '/payments', $payload);
    }

    public function fetchPayment(string $paymentId): array
    {
        return $this->send('get', '/payments/'.rawurlencode($paymentId));
    }

    /** Ключ идемпотентности передаём явно: повторный вебхук не создаст второй возврат. */
    public function createRefund(array $payload, string $idempotenceKey): array
    {
        return $this->send('post', '/refunds', $payload, $idempotenceKey);
    }

    private function send(string $method, string $path, array $payload = [], ?string $idempotenceKey = null): array
    {
        $shopId = (string) config('services.yookassa.shop_id');
        $secret = (string) config('services.yookassa.secret_key');
        if ($shopId === '' || $secret === '') {
            throw new RuntimeException('YooKassa не настроена: задайте YOOKASSA_SHOP_ID и YOOKASSA_SECRET_KEY.');
        }

        /** @var PendingRequest $http */
        $http = Http::baseUrl((string) config('services.yookassa.api_base'))
            ->withBasicAuth($shopId, $secret)
            ->acceptJson()
            ->timeout(15);

        if ($method === 'post') {
            $key = $idempotenceKey ?? (string) Str::uuid();
            $response = $http->withHeaders(['Idempotence-Key' => $key])->post($path, $payload);
        } else {
            $response = $http->get($path);
        }

        if (! $response->successful()) {
            throw new RuntimeException('YooKassa вернула ошибку '.$response->status().': '.$response->body());
        }

        return $response->json() ?? [];
    }
}
