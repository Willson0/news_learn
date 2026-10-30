<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Third Party Services
    |--------------------------------------------------------------------------
    |
    | This file is for storing the credentials for third party services such
    | as Resend, Postmark, AWS, and more. This file provides the de facto
    | location for this type of information, allowing packages to have
    | a conventional file to locate the various service credentials.
    |
    */

    'postmark' => [
        'key' => env('POSTMARK_API_KEY'),
    ],

    'resend' => [
        'key' => env('RESEND_API_KEY'),
    ],

    'ses' => [
        'key' => env('AWS_ACCESS_KEY_ID'),
        'secret' => env('AWS_SECRET_ACCESS_KEY'),
        'region' => env('AWS_DEFAULT_REGION', 'us-east-1'),
    ],

    'slack' => [
        'notifications' => [
            'bot_user_oauth_token' => env('SLACK_BOT_USER_OAUTH_TOKEN'),
            'channel' => env('SLACK_BOT_USER_DEFAULT_CHANNEL'),
        ],
    ],

    'yandex' => [
        'client_id' => env('YANDEX_CLIENT_ID'),
        'client_secret' => env('YANDEX_CLIENT_SECRET'),
        'redirect_uri' => env('YANDEX_REDIRECT_URI', env('APP_URL').'/api/auth/yandex/callback'),
        'app_scheme' => env('YANDEX_APP_SCHEME', 'karta'),
    ],

    // Т-Касса (бывш. Тинькофф Касса): платежи, привязка карты и автопродление.
    'tbank' => [
        'terminal_key' => env('TBANK_TERMINAL_KEY'),
        'password' => env('TBANK_PASSWORD'),
        // TBANK_MODE=test — тестовый терминал (тестовые карты), иначе боевой. TBANK_API_BASE перекрывает адрес целиком.
        'mode' => env('TBANK_MODE', 'prod'),
        'api_base' => env('TBANK_API_BASE') ?: (env('TBANK_MODE', 'prod') === 'test'
            ? 'https://rest-api-test.tinkoff.ru/v2'
            : 'https://securepay.tinkoff.ru/v2'),
        'notification_url' => env('TBANK_NOTIFICATION_URL', 'https://wilflw.ru/api/tbank/webhook'),
        'return_url' => env('TBANK_RETURN_URL', 'https://wilflw.ru/api/billing/card/return'),
        'fail_url' => env('TBANK_FAIL_URL', 'https://wilflw.ru/api/billing/card/return?status=cancel'),
        'bind_amount' => (int) env('TBANK_BIND_AMOUNT', 1),              // рубли, возвращаются после привязки
        'subscription_amount' => (int) env('TBANK_SUBSCRIPTION_AMOUNT', 299),
        'subscription_months' => (int) env('TBANK_SUBSCRIPTION_MONTHS', 1),
        'app_scheme' => env('TBANK_APP_SCHEME', 'karta'),
        // Чек 54-ФЗ (если на терминале включена фискализация): osn, usn_income, usn_income_outcome, esn, patent.
        'taxation' => env('TBANK_TAXATION'),
        'tax' => env('TBANK_TAX', 'none'),
    ],

    // Firebase Cloud Messaging (push в мобильных приложениях).
    'fcm' => [
        'project_id' => env('FCM_PROJECT_ID'),
        'credentials' => env('FCM_CREDENTIALS'),   // путь к JSON сервисного аккаунта
    ],

    'telegram' => [
        'bot_token' => env('TELEGRAM_BOT_TOKEN'),
        'allow_insecure' => (bool) env('TELEGRAM_ALLOW_INSECURE', false),
    ],

];
