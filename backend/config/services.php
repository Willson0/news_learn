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

    'yookassa' => [
        'shop_id' => env('YOOKASSA_SHOP_ID'),
        'secret_key' => env('YOOKASSA_SECRET_KEY'),
        'api_base' => env('YOOKASSA_API_BASE', 'https://api.yookassa.ru/v3'),
        'return_url' => env('YOOKASSA_RETURN_URL', 'https://wilflw.ru/api/billing/card/return'),
        'bind_amount' => env('YOOKASSA_BIND_AMOUNT', '1.00'),
        'subscription_amount' => env('YOOKASSA_SUBSCRIPTION_AMOUNT', '299.00'),
        'subscription_months' => (int) env('YOOKASSA_SUBSCRIPTION_MONTHS', 1),
        'app_scheme' => env('YOOKASSA_APP_SCHEME', 'karta'),
    ],

    'telegram' => [
        'bot_token' => env('TELEGRAM_BOT_TOKEN'),
        'allow_insecure' => (bool) env('TELEGRAM_ALLOW_INSECURE', false),
    ],

];
