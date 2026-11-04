<?php

namespace App\Support;

use Carbon\CarbonInterface;
use Illuminate\Support\Carbon;

/**
 * Срок одного периода подписки: N месяцев (фиксированные 1/3/6/12) или произвольное число дней,
 * которое админ вписывает при выставлении счёта. Дни, если заданы, главнее месяцев.
 */
final class Period
{
    private function __construct(
        public readonly int $months,
        public readonly ?int $days,
    ) {}

    public static function months(int $months): self
    {
        return new self(max(1, $months), null);
    }

    public static function days(int $days): self
    {
        return new self(0, max(1, $days));
    }

    /** Из колонок months/days модели; без того и другого — срок по умолчанию из настроек. */
    public static function of(?int $months, ?int $days, ?int $fallbackMonths = null): self
    {
        if ($days !== null && $days > 0) {
            return self::days($days);
        }

        return self::months($months !== null && $months > 0 ? $months : ($fallbackMonths ?? 1));
    }

    public function isDays(): bool
    {
        return $this->days !== null;
    }

    /** Колонки для записи в subscriptions/invoices/payments. */
    public function columns(): array
    {
        return ['months' => $this->months, 'days' => $this->days];
    }

    /** Дата окончания периода, начатого с $base. */
    public function extend(CarbonInterface|string $base): Carbon
    {
        $date = Carbon::parse($base);

        return $this->isDays() ? $date->addDays($this->days) : $date->addMonths($this->months);
    }

    /** «1 месяц», «3 месяца», «45 дней». */
    public function label(): string
    {
        return $this->isDays()
            ? $this->days.' '.self::plural($this->days, 'день', 'дня', 'дней')
            : $this->months.' '.self::plural($this->months, 'месяц', 'месяца', 'месяцев');
    }

    /** «1 мес.», «45 дн.» — для цены и таблиц. */
    public function short(): string
    {
        return $this->isDays() ? $this->days.' дн.' : $this->months.' мес.';
    }

    public static function plural(int $n, string $one, string $few, string $many): string
    {
        $n100 = $n % 100;
        $n10 = $n % 10;

        return match (true) {
            $n100 >= 11 && $n100 <= 14 => $many,
            $n10 === 1 => $one,
            $n10 >= 2 && $n10 <= 4 => $few,
            default => $many,
        };
    }
}
