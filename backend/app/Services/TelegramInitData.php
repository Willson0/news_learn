<?php

namespace App\Services;

use Carbon\Carbon;

/**
 * Проверка и разбор initData, который Telegram передаёт мини-приложению.
 *
 * Алгоритм (https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app):
 *  1. Разбираем query-строку в пары ключ=значение.
 *  2. Вынимаем hash, остальные пары сортируем и склеиваем через \n в data_check_string.
 *  3. secret_key = HMAC_SHA256(bot_token, "WebAppData").
 *  4. Сверяем hash с HMAC_SHA256(data_check_string, secret_key).
 */
class TelegramInitData
{
    public function __construct(private readonly ?string $botToken) {}

    /**
     * Возвращает распарсенные данные (с декодированным полем user), если подпись
     * верна и не просрочена; иначе null.
     *
     * @return array<string, mixed>|null
     */
    public function validate(string $initData, int $maxAgeSeconds = 86400): ?array
    {
        if ($this->botToken === null || $this->botToken === '' || $initData === '') {
            return null;
        }

        parse_str($initData, $params);

        if (! isset($params['hash']) || ! is_string($params['hash'])) {
            return null;
        }

        $hash = $params['hash'];
        unset($params['hash']);

        ksort($params);

        $pairs = [];
        foreach ($params as $key => $value) {
            $pairs[] = $key.'='.$value;
        }
        $dataCheckString = implode("\n", $pairs);

        $secretKey = hash_hmac('sha256', $this->botToken, 'WebAppData', true);
        $calculated = hash_hmac('sha256', $dataCheckString, $secretKey);

        if (! hash_equals($calculated, $hash)) {
            return null;
        }

        // Защита от повторного использования старого initData.
        if (isset($params['auth_date']) && $maxAgeSeconds > 0) {
            $authDate = (int) $params['auth_date'];
            if ($authDate > 0 && Carbon::createFromTimestamp($authDate)->addSeconds($maxAgeSeconds)->isPast()) {
                return null;
            }
        }

        if (isset($params['user']) && is_string($params['user'])) {
            $user = json_decode($params['user'], true);
            if (is_array($user)) {
                $params['user'] = $user;
            }
        }

        return $params;
    }
}
