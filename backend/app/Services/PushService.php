<?php

namespace App\Services;

use App\Models\AppSetting;
use App\Models\DeviceToken;
use App\Models\User;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Push-уведомления через Firebase Cloud Messaging (HTTP v1) — и на Android, и на iOS
 * (на iOS FCM передаёт уведомление через APNs, если в Firebase загружен APNs-ключ Apple).
 *
 * Ключ сервисного аккаунта Firebase (JSON) загружается в настройках админки; запасной
 * вариант — FCM_PROJECT_ID и FCM_CREDENTIALS (путь к JSON) в .env. Без ключа Google не
 * принимает отправку ни на одну платформу: отправка пропускается, в лог пишется предупреждение.
 */
class PushService
{
    private const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

    private const TOKEN_URL = 'https://oauth2.googleapis.com/token';

    /** Канал уведомлений Android-приложения (KartaMessagingService.CHANNEL_ID). */
    private const ANDROID_CHANNEL = 'karta_default';

    /** @var array<string, mixed>|null|false */
    private array|null|false $credentials = false;

    public function configured(): bool
    {
        return $this->credentials() !== null;
    }

    /** Откуда взят ключ: «admin» (загружен в админке), «env» или null. */
    public function source(): ?string
    {
        if ($this->fromSettings() !== null) {
            return 'admin';
        }

        return $this->fromEnv() !== null ? 'env' : null;
    }

    public function projectId(): ?string
    {
        return $this->credentials()['project_id'] ?? null;
    }

    /**
     * Проверяет JSON ключа сервисного аккаунта и возвращает его разобранным (или текст ошибки).
     *
     * @return array<string, mixed>|string
     */
    public static function parseServiceAccount(string $json, bool $strict = true): array|string
    {
        $key = json_decode($json, true);
        if (! is_array($key)) {
            return 'Это не JSON. Нужен файл ключа сервисного аккаунта Firebase (.json).';
        }
        if ($strict && ($key['type'] ?? null) !== 'service_account') {
            return 'Нужен ключ сервисного аккаунта (в JSON должно быть "type": "service_account"). '
                .'Файл google-services.json / GoogleService-Info.plist для приложений не подходит.';
        }
        foreach ($strict ? ['project_id', 'client_email', 'private_key'] : ['client_email', 'private_key'] as $field) {
            if (empty($key[$field]) || ! is_string($key[$field])) {
                return "В ключе нет поля {$field}.";
            }
        }
        if (openssl_pkey_get_private($key['private_key']) === false) {
            return 'Закрытый ключ в файле повреждён.';
        }

        return $key;
    }

    /** @return array<string, mixed>|null */
    private function credentials(): ?array
    {
        if ($this->credentials === false) {
            $this->credentials = $this->fromSettings() ?? $this->fromEnv();
        }

        return $this->credentials;
    }

    /** @return array<string, mixed>|null */
    private function fromSettings(): ?array
    {
        try {
            $json = AppSetting::secret('fcm_credentials');
        } catch (\Throwable) {
            return null; // таблицы настроек ещё нет
        }
        if ($json === null) {
            return null;
        }
        $key = self::parseServiceAccount($json);

        return is_array($key) ? $key : null;
    }

    /** @return array<string, mixed>|null */
    private function fromEnv(): ?array
    {
        $path = (string) config('services.fcm.credentials');
        if ((string) config('services.fcm.project_id') === '' || $path === '' || ! is_file($path)) {
            return null;
        }
        // Из .env project_id задаётся отдельно (FCM_PROJECT_ID), поэтому проверка мягче.
        $key = self::parseServiceAccount((string) file_get_contents($path), strict: false);
        if (! is_array($key)) {
            return null;
        }
        $key['project_id'] = (string) config('services.fcm.project_id');

        return $key;
    }

    /**
     * Отправляет уведомление всем устройствам пользователя. Возвращает число успешных доставок.
     *
     * @param  array<string, string>  $data
     */
    public function sendToUser(User $user, string $title, string $body, array $data = []): int
    {
        return $this->sendToTokens(
            $user->deviceTokens()->pluck('token')->all(),
            $title,
            $body,
            $data,
        );
    }

    /**
     * @param  array<int, string>  $tokens
     * @param  array<string, string>  $data
     */
    public function sendToTokens(array $tokens, string $title, string $body, array $data = []): int
    {
        return $this->deliver($tokens, $title, $body, $data)['sent'];
    }

    /**
     * Отправка на устройства с разбивкой по платформам — для рассылки из админки.
     * Ошибки одной платформы не мешают другой: если у Apple не настроен APNs, Android всё равно получит.
     *
     * @param  iterable<DeviceToken>  $devices
     * @param  array<string, string>  $data
     * @return array{configured: bool, platforms: array<string, array{devices: int, sent: int, failed: int}>, apns_missing: bool, errors: array<int, string>}
     */
    public function sendToDevices(iterable $devices, string $title, string $body, array $data = []): array
    {
        $byPlatform = [];
        foreach ($devices as $device) {
            $platform = in_array($device->platform, ['android', 'ios'], true) ? $device->platform : 'other';
            $byPlatform[$platform][$device->token] = true;
        }

        $report = ['configured' => $this->configured(), 'platforms' => [], 'apns_missing' => false, 'errors' => []];
        foreach ($byPlatform as $platform => $tokens) {
            $result = $this->deliver(array_keys($tokens), $title, $body, $data);
            $report['platforms'][$platform] = [
                'devices' => count($tokens),
                'sent' => $result['sent'],
                'failed' => count($tokens) - $result['sent'],
            ];
            $report['apns_missing'] = $report['apns_missing'] || $result['apns_missing'];
            $report['errors'] = array_values(array_unique([...$report['errors'], ...$result['errors']]));
        }

        return $report;
    }

    /**
     * @param  array<int, string>  $tokens
     * @param  array<string, string>  $data
     * @return array{sent: int, apns_missing: bool, errors: array<int, string>}
     */
    private function deliver(array $tokens, string $title, string $body, array $data): array
    {
        $result = ['sent' => 0, 'apns_missing' => false, 'errors' => []];
        if ($tokens === []) {
            return $result;
        }
        if (! $this->configured()) {
            Log::warning('Push не отправлен: FCM не настроен', ['tokens' => count($tokens)]);

            return $result;
        }

        try {
            $accessToken = $this->accessToken();
        } catch (\Throwable $e) {
            Log::warning('FCM: не удалось получить токен доступа', ['error' => $e->getMessage()]);
            Cache::forget($this->tokenCacheKey());
            $result['errors'][] = 'Google отклонил ключ Firebase: '.$e->getMessage();

            return $result;
        }

        $url = 'https://fcm.googleapis.com/v1/projects/'.$this->projectId().'/messages:send';
        $payloadData = array_map('strval', $data);

        foreach ($tokens as $token) {
            try {
                $response = Http::withToken($accessToken)->acceptJson()->timeout(10)->post($url, [
                    'message' => [
                        'token' => $token,
                        'notification' => ['title' => $title, 'body' => $body],
                        'data' => $payloadData === [] ? (object) [] : $payloadData,
                        // Высокий приоритет и наш канал: уведомление приходит сразу, а не в «пачке» Doze.
                        'android' => [
                            'priority' => 'high',
                            'notification' => ['channel_id' => self::ANDROID_CHANNEL, 'sound' => 'default'],
                        ],
                        'apns' => [
                            'headers' => ['apns-priority' => '10'],
                            'payload' => ['aps' => ['sound' => 'default']],
                        ],
                    ],
                ]);
            } catch (\Throwable $e) {
                $result['errors'][] = 'Нет связи с FCM: '.$e->getMessage();

                continue;
            }

            if ($response->successful()) {
                $result['sent']++;

                continue;
            }

            $status = (string) $response->json('error.status');
            $code = (string) collect((array) $response->json('error.details'))
                ->pluck('errorCode')->filter()->first();

            // Устройство отписалось или токен устарел: убираем его, чтобы не слать впустую.
            if (in_array($status, ['NOT_FOUND', 'UNREGISTERED'], true) || $code === 'UNREGISTERED') {
                DeviceToken::where('token', $token)->delete();

                continue;
            }
            // Для iOS в Firebase не загружен (или неверен) APNs-ключ Apple.
            if ($code === 'THIRD_PARTY_AUTH_ERROR' || $status === 'THIRD_PARTY_AUTH_ERROR') {
                $result['apns_missing'] = true;

                continue;
            }

            Log::warning('FCM: ошибка отправки', ['status' => $response->status(), 'body' => $response->body()]);
            $result['errors'][] = trim('FCM '.$response->status().': '.($response->json('error.message') ?? $status));
        }

        $result['errors'] = array_values(array_unique($result['errors']));

        return $result;
    }

    private function tokenCacheKey(): string
    {
        // Ключ кэша зависит от аккаунта: после загрузки нового ключа старый токен не используется.
        return 'fcm.access_token.'.md5((string) ($this->credentials()['client_email'] ?? ''));
    }

    private function accessToken(): string
    {
        return Cache::remember($this->tokenCacheKey(), 3000, function () {
            $key = (array) $this->credentials();
            $now = time();

            $header = $this->b64(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
            $claims = $this->b64(json_encode([
                'iss' => $key['client_email'],
                'scope' => self::SCOPE,
                'aud' => self::TOKEN_URL,
                'iat' => $now,
                'exp' => $now + 3600,
            ]));

            openssl_sign("{$header}.{$claims}", $signature, $key['private_key'], OPENSSL_ALGO_SHA256);

            $response = Http::asForm()->post(self::TOKEN_URL, [
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => "{$header}.{$claims}.".$this->b64($signature),
            ])->throw();

            $token = (string) $response->json('access_token');
            if ($token === '') {
                throw new \RuntimeException('пустой access_token');
            }

            return $token;
        });
    }

    private function b64(string $raw): string
    {
        return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    }
}
