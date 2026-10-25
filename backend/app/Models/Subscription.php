<?php

namespace App\Models;

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

    /** Дней после окончания периода на автосписание: пока оно идёт, доступ не закрываем. */
    public const RENEWAL_GRACE_DAYS = 3;

    /**
     * Доступ по подписке открыт сейчас. Дата окончания действует всегда: включённое автопродление
     * даёт лишь несколько дней на списание, а не бессрочный доступ.
     */
    public function grantsAccessNow(): bool
    {
        if (! $this->active) {
            return false;
        }
        if ($this->until === null) {
            return true;
        }

        $grace = $this->auto_pay ? self::RENEWAL_GRACE_DAYS : 0;

        return $this->until->copy()->addDays($grace)->gte(today());
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
