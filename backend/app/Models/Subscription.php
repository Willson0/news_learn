<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Subscription extends Model
{
    protected $fillable = [
        'user_id', 'active', 'plan', 'amount', 'months', 'until', 'auto_pay', 'payment_method', 'last_payment_id',
    ];

    protected $casts = [
        'active' => 'boolean',
        'auto_pay' => 'boolean',
        'until' => 'date',
    ];

    /** Подписка считается по московскому календарю: «до 25.10» значит доступ весь день 25.10 по Москве. */
    public const TIMEZONE = 'Europe/Moscow';

    /** Сегодняшняя дата по Москве, Y-m-d. */
    public static function today(): string
    {
        return now(self::TIMEZONE)->toDateString();
    }

    /**
     * Единственное правило «подписка активна»: флаг active и дата окончания не раньше сегодняшней
     * (день окончания включительно). Без даты подписка не активна. Это же правило повторяет
     * scopeGrantingAccess() для запросов, а приложения проверяют его ещё раз у себя по полю until.
     */
    public function grantsAccessNow(): bool
    {
        return $this->active
            && $this->until !== null
            && $this->until->toDateString() >= self::today();
    }

    /**
     * Тариф пользователю определён: админ задал сумму (счёт оплачен или условия изменены),
     * либо подписка уже оплачивалась картой. Новый аккаунт тарифа не имеет: оплатить подписку
     * он может только по счёту, который выставит администратор (макет «Тарифный план: Не определен»).
     */
    public function tariffDefined(): bool
    {
        return $this->amount !== null || $this->last_payment_id !== null;
    }

    /** То же правило, что grantsAccessNow(), для выборок (админка, рассылки, аналитика). */
    public function scopeGrantingAccess(Builder $query): Builder
    {
        return $query->where('active', true)->whereNotNull('until')->whereDate('until', '>=', self::today());
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
