<?php

namespace App\Models;

use App\Support\Period;
use App\Support\SubscriptionPayload;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Once;

/**
 * Ключ-значение для настроек, которые меняются в админке. Значения хранятся как JSON.
 */
class AppSetting extends Model
{
    protected $primaryKey = 'key';

    public $incrementing = false;

    protected $keyType = 'string';

    protected $fillable = ['key', 'value'];

    /**
     * Все настройки читаются одним запросом на запрос HTTP (once сбрасывается между запросами
     * и тестами): при загрузке приложения нужно сразу несколько ключей почты.
     */
    private static function cachedValues(): array
    {
        return once(fn () => self::query()->pluck('value', 'key')->all());
    }

    public static function get(string $key, mixed $default = null): mixed
    {
        $raw = self::cachedValues()[$key] ?? null;

        if ($raw === null) {
            return $default;
        }

        return json_decode($raw, true);
    }

    public static function put(string $key, mixed $value): void
    {
        self::updateOrCreate(['key' => $key], ['value' => $value === null ? null : json_encode($value, JSON_UNESCAPED_UNICODE)]);
        Once::flush();
    }

    /**
     * Секрет (пароль SMTP, ключ Firebase): в базе лежит зашифрованным ключом приложения (APP_KEY).
     * Если значение не расшифровывается (сменили APP_KEY), считаем, что его нет.
     */
    public static function secret(string $key): ?string
    {
        $raw = self::get($key);
        if (! is_string($raw) || $raw === '') {
            return null;
        }
        try {
            return Crypt::decryptString($raw);
        } catch (\Throwable) {
            return null;
        }
    }

    public static function putSecret(string $key, ?string $value): void
    {
        self::put($key, $value === null || $value === '' ? null : Crypt::encryptString($value));
    }

    /** Цена периода по умолчанию, рубли. Меняется в настройках админки. */
    public static function defaultPrice(): int
    {
        return (int) self::get('default_price', (int) config('services.tbank.subscription_amount'));
    }

    /**
     * Стоимость подписки для приложения, например «990 ₽ / 2 мес.». Берётся из подписки
     * пользователя, а если её ещё нет или в ней не задана цена, то из настроек по умолчанию
     * (та же логика, что и в панели администратора).
     */
    public static function subscriptionPriceLabel(?Subscription $sub = null): string
    {
        $amount = $sub?->amount ?? self::defaultPrice();

        return SubscriptionPayload::label($amount, $sub?->period() ?? Period::months(self::defaultMonths()));
    }

    /** Срок периода по умолчанию, месяцы. */
    public static function defaultMonths(): int
    {
        return (int) self::get('default_months', (int) config('services.tbank.subscription_months', 1));
    }
}
