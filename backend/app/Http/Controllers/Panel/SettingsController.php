<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\AppSetting;
use App\Services\PushService;
use App\Support\MailSettings;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Mail;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Настройки приложения: цена по умолчанию, документы, ключи сервисов.
 * Секреты наружу отдаются только в замаскированном виде.
 */
class SettingsController extends Controller
{
    public function show(): JsonResponse
    {
        $push = app(PushService::class);

        return response()->json(['data' => [
            'default_price' => AppSetting::defaultPrice(),
            'default_months' => AppSetting::defaultMonths(),
            'documents' => AppSetting::get('documents', []),
            'tradingview_api_key' => $this->mask(AppSetting::get('tradingview_api_key')),
            'email_api_key' => $this->mask(AppSetting::get('email_api_key')),
            'fcm_configured' => $push->configured(),
            'fcm_source' => $push->source(),
            'fcm_project_id' => $push->projectId(),
            'mail' => MailSettings::payload(),
        ]]);
    }

    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'default_price' => ['sometimes', 'integer', 'min:1', 'max:1000000'],
            'default_months' => ['sometimes', 'integer', 'min:1', 'max:12'],
            'documents' => ['sometimes', 'array', 'max:30'],
            'documents.*.title' => ['required_with:documents', 'string', 'max:120'],
            'documents.*.url' => ['required_with:documents', 'url:http,https', 'max:2048'],
            'tradingview_api_key' => ['sometimes', 'nullable', 'string', 'max:255'],
            'email_api_key' => ['sometimes', 'nullable', 'string', 'max:255'],
            // Почта для кодов: способ отправки и SMTP-сервер.
            'mail' => ['sometimes', 'array'],
            'mail.mailer' => ['nullable', Rule::in(MailSettings::MAILERS)],
            'mail.host' => ['nullable', 'string', 'max:255', 'required_if:mail.mailer,smtp'],
            'mail.port' => ['nullable', 'integer', 'min:1', 'max:65535', 'required_if:mail.mailer,smtp'],
            'mail.encryption' => ['nullable', Rule::in(MailSettings::ENCRYPTIONS)],
            'mail.username' => ['nullable', 'string', 'max:255'],
            'mail.password' => ['nullable', 'string', 'max:500'],
            'mail.clear_password' => ['sometimes', 'boolean'],
            'mail.from_address' => ['nullable', 'email', 'max:255', 'required_if:mail.mailer,smtp,resend'],
            'mail.from_name' => ['nullable', 'string', 'max:120'],
            // Ключ сервисного аккаунта Firebase (содержимое JSON) или признак удаления.
            'fcm_credentials' => ['sometimes', 'nullable', 'string', 'max:20000'],
            'fcm_clear' => ['sometimes', 'boolean'],
        ], [
            'documents.*.url.url' => 'Ссылка документа должна начинаться с http:// или https://',
            'mail.host.required_if' => 'Укажите SMTP-сервер',
            'mail.port.required_if' => 'Укажите порт SMTP',
            'mail.from_address.required_if' => 'Укажите адрес отправителя',
            'mail.from_address.email' => 'Адрес отправителя указан неверно',
        ]);

        if (! empty($data['fcm_credentials'])) {
            $key = PushService::parseServiceAccount($data['fcm_credentials']);
            if (is_string($key)) {
                throw ValidationException::withMessages(['fcm_credentials' => $key]);
            }
            AppSetting::putSecret('fcm_credentials', $data['fcm_credentials']);
        } elseif (! empty($data['fcm_clear'])) {
            AppSetting::putSecret('fcm_credentials', null);
        }

        if (isset($data['mail'])) {
            $mail = $data['mail'];
            if (($mail['mailer'] ?? null) === 'resend' && empty($data['email_api_key']) && ! AppSetting::get('email_api_key')) {
                throw ValidationException::withMessages(['email_api_key' => 'Для Resend нужен API-ключ']);
            }
            MailSettings::update($mail);
        }

        if (isset($data['default_price'])) {
            AppSetting::put('default_price', $data['default_price']);
        }
        if (isset($data['default_months'])) {
            AppSetting::put('default_months', $data['default_months']);
        }
        if (array_key_exists('documents', $data)) {
            AppSetting::put('documents', array_values($data['documents']));
        }
        // Секреты: пришла маска или пусто — значение не трогаем, пустое поле очищает ключ.
        foreach (['tradingview_api_key', 'email_api_key'] as $key) {
            if (! array_key_exists($key, $data) || str_starts_with((string) $data[$key], '•')) {
                continue;
            }
            AppSetting::put($key, $data[$key] ?: null);
        }

        MailSettings::reload();

        return $this->show();
    }

    /**
     * Тестовое письмо с текущими настройками почты: сразу видно, работает ли SMTP.
     */
    public function testMail(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email', 'max:255'],
        ], [
            'email.required' => 'Укажите адрес для проверки',
            'email.email' => 'Адрес указан неверно',
        ]);

        MailSettings::reload();

        if (config('mail.default') === 'log') {
            return response()->json([
                'ok' => false,
                'message' => 'Почта отключена (режим «только лог»): письма не отправляются, коды пишутся в лог сервера.',
            ]);
        }

        try {
            Mail::raw('Тестовое письмо из админки «Карта ликвидности». Почта для кодов настроена правильно.', function ($m) use ($data) {
                $m->to($data['email'])->subject('Проверка почты — Карта ликвидности');
            });
        } catch (\Throwable $e) {
            return response()->json(['ok' => false, 'message' => 'Письмо не отправлено: '.$e->getMessage()]);
        }

        return response()->json(['ok' => true, 'message' => "Письмо отправлено на {$data['email']}. Проверьте входящие и «Спам»."]);
    }

    private function mask(?string $secret): ?string
    {
        if (! $secret) {
            return null;
        }

        return '•••• '.substr($secret, -4);
    }
}
