<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

/**
 * Ключ-значение для настроек, которые меняются в админке. Значения хранятся как JSON.
 */
class AppSetting extends Model
{
    protected $primaryKey = 'key';

    public $incrementing = false;

    protected $keyType = 'string';

    protected $fillable = ['key', 'value'];

    public static function get(string $key, mixed $default = null): mixed
    {
        $row = self::find($key);

        if (! $row || $row->value === null) {
            return $default;
        }

        return json_decode($row->value, true);
    }

    public static function put(string $key, mixed $value): void
    {
        self::updateOrCreate(['key' => $key], ['value' => $value === null ? null : json_encode($value, JSON_UNESCAPED_UNICODE)]);
    }

    /** Цена периода по умолчанию, рубли. Меняется в настройках админки. */
    public static function defaultPrice(): int
    {
        return (int) self::get('default_price', (int) config('services.yookassa.subscription_amount'));
    }

    /**
     * Стоимость подписки для приложения, например «990 ₽ / 2 мес.». Берётся из подписки
     * пользователя, а если её ещё нет или в ней не задана цена, то из настроек по умолчанию
     * (та же логика, что и в панели администратора).
     */
    public static function subscriptionPriceLabel(?Subscription $sub = null): string
    {
        $amount = $sub?->amount ?? self::defaultPrice();
        $months = $sub?->months ?? self::defaultMonths();

        return number_format($amount, 0, ',', ' ').' ₽ / '.$months.' мес.';
    }

    /** Срок периода по умолчанию, месяцы. */
    public static function defaultMonths(): int
    {
        return (int) self::get('default_months', (int) config('services.yookassa.subscription_months', 1));
    }
}
