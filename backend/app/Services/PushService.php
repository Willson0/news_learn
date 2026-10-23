<?php

namespace App\Services;

use App\Models\DeviceToken;
use App\Models\User;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Push-уведомления через Firebase Cloud Messaging (HTTP v1).
 * Нужны FCM_PROJECT_ID и FCM_CREDENTIALS (путь к JSON сервисного аккаунта).
 * Без них отправка тихо пропускается, а в лог пишется предупреждение.
 */
class PushService
{
    private const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

    private const TOKEN_URL = 'https://oauth2.googleapis.com/token';

    public function configured(): bool
    {
        $path = (string) config('services.fcm.credentials');

        return (string) config('services.fcm.project_id') !== ''
            && $path !== '' && is_file($path);
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
        if ($tokens === []) {
            return 0;
        }
        if (! $this->configured()) {
            Log::warning('Push не отправлен: FCM не настроен', ['tokens' => count($tokens)]);

            return 0;
        }

        $accessToken = $this->accessToken();
        $url = 'https://fcm.googleapis.com/v1/projects/'.config('services.fcm.project_id').'/messages:send';
        $sent = 0;

        foreach ($tokens as $token) {
            $response = Http::withToken($accessToken)->acceptJson()->timeout(10)->post($url, [
                'message' => [
                    'token' => $token,
                    'notification' => ['title' => $title, 'body' => $body],
                    'data' => array_map('strval', $data),
                ],
            ]);

            if ($response->successful()) {
                $sent++;

                continue;
            }

            // Устройство отписалось или токен устарел: убираем его, чтобы не слать впустую.
            if (in_array($response->json('error.status'), ['NOT_FOUND', 'UNREGISTERED'], true)) {
                DeviceToken::where('token', $token)->delete();
            } else {
                Log::warning('FCM: ошибка отправки', ['status' => $response->status(), 'body' => $response->body()]);
            }
        }

        return $sent;
    }

    private function accessToken(): string
    {
        return Cache::remember('fcm.access_token', 3000, function () {
            $key = json_decode((string) file_get_contents((string) config('services.fcm.credentials')), true);
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

            return (string) $response->json('access_token');
        });
    }

    private function b64(string $raw): string
    {
        return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    }
}
