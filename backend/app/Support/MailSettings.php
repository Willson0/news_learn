<?php

namespace App\Support;

use App\Models\AppSetting;
use Illuminate\Support\Facades\Mail;

/**
 * Почта для кодов подтверждения и восстановления, настроенная в админке.
 *
 * Способы: «smtp» — любой SMTP-сервер (Яндекс, Mail.ru, Gmail, свой); «resend» — Resend
 * через его SMTP-шлюз (smtp.resend.com, логин resend, пароль — API-ключ), поэтому отдельный
 * PHP-пакет Resend не нужен; «log» — письма только пишутся в лог. Пока способ в админке не
 * выбран, действуют MAIL_* из .env, как раньше.
 */
class MailSettings
{
    public const MAILERS = ['smtp', 'resend', 'log'];

    public const ENCRYPTIONS = ['ssl', 'tls', 'none'];

    /** Подставляет сохранённые настройки в конфиг почты. Вызывается при загрузке приложения. */
    public static function apply(): void
    {
        $mailer = AppSetting::get('mail_mailer');

        // Ключ Resend из админки важнее .env (как было и раньше).
        $resendKey = AppSetting::get('email_api_key');
        if (is_string($resendKey) && $resendKey !== '') {
            config(['services.resend.key' => $resendKey]);
        }

        if ($mailer === 'smtp') {
            $encryption = (string) AppSetting::get('mail_encryption', 'tls');
            config([
                'mail.default' => 'smtp',
                'mail.mailers.smtp.url' => null,
                'mail.mailers.smtp.host' => (string) AppSetting::get('mail_host', ''),
                'mail.mailers.smtp.port' => (int) AppSetting::get('mail_port', 587),
                'mail.mailers.smtp.scheme' => $encryption === 'ssl' ? 'smtps' : 'smtp',
                'mail.mailers.smtp.username' => AppSetting::get('mail_username'),
                'mail.mailers.smtp.password' => AppSetting::secret('mail_password'),
                'mail.mailers.smtp.timeout' => 15,
            ]);
        } elseif ($mailer === 'resend') {
            config([
                'mail.default' => 'smtp',
                'mail.mailers.smtp.url' => null,
                'mail.mailers.smtp.host' => 'smtp.resend.com',
                'mail.mailers.smtp.port' => 465,
                'mail.mailers.smtp.scheme' => 'smtps',
                'mail.mailers.smtp.username' => 'resend',
                'mail.mailers.smtp.password' => (string) config('services.resend.key'),
                'mail.mailers.smtp.timeout' => 15,
            ]);
        } elseif ($mailer === 'log') {
            config(['mail.default' => 'log']);
        }

        $from = AppSetting::get('mail_from_address');
        if (is_string($from) && $from !== '') {
            config(['mail.from.address' => $from]);
        }
        $fromName = AppSetting::get('mail_from_name');
        if (is_string($fromName) && $fromName !== '') {
            config(['mail.from.name' => $fromName]);
        }
    }

    /** Применяет настройки заново в текущем запросе (после сохранения, перед тестовым письмом). */
    public static function reload(): void
    {
        self::apply();
        Mail::forgetMailers();
    }

    /**
     * Настройки для формы админки. Пароль наружу не отдаётся — только признак, что он сохранён.
     *
     * @return array<string, mixed>
     */
    public static function payload(): array
    {
        return [
            'mailer' => AppSetting::get('mail_mailer'),
            'host' => AppSetting::get('mail_host'),
            'port' => AppSetting::get('mail_port'),
            'encryption' => AppSetting::get('mail_encryption', 'tls'),
            'username' => AppSetting::get('mail_username'),
            'password_set' => AppSetting::secret('mail_password') !== null,
            'from_address' => AppSetting::get('mail_from_address'),
            'from_name' => AppSetting::get('mail_from_name'),
            // Что действует сейчас (с учётом .env, если в админке ничего не выбрано).
            'active' => self::activeLabel(),
        ];
    }

    private static function activeLabel(): string
    {
        $mailer = AppSetting::get('mail_mailer');
        $source = in_array($mailer, self::MAILERS, true) ? 'настройки админки' : 'файл .env (MAIL_MAILER)';
        $default = (string) config('mail.default');
        $what = match (true) {
            $mailer === 'resend' => 'Resend (smtp.resend.com)',
            $default === 'smtp' => 'SMTP '.config('mail.mailers.smtp.host').':'.config('mail.mailers.smtp.port'),
            $default === 'log' => 'только лог, письма не уходят',
            default => $default,
        };

        return "{$what} · {$source}";
    }

    /**
     * Сохраняет поля из запроса. Пустой пароль не меняет сохранённый (поле «оставьте пустым»).
     *
     * @param  array<string, mixed>  $data
     */
    public static function update(array $data): void
    {
        if (array_key_exists('mailer', $data)) {
            AppSetting::put('mail_mailer', $data['mailer'] ?: null);
        }
        foreach (['host', 'encryption', 'username', 'from_address', 'from_name'] as $field) {
            if (array_key_exists($field, $data)) {
                $value = is_string($data[$field]) ? trim($data[$field]) : $data[$field];
                AppSetting::put('mail_'.$field, $value === '' ? null : $value);
            }
        }
        if (array_key_exists('port', $data)) {
            AppSetting::put('mail_port', $data['port'] ? (int) $data['port'] : null);
        }
        if (! empty($data['password'])) {
            AppSetting::putSecret('mail_password', (string) $data['password']);
        }
        if (! empty($data['clear_password'])) {
            AppSetting::putSecret('mail_password', null);
        }
    }
}
