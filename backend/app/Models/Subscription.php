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

    /**
     * Доступ по подписке открыт сейчас. Подписка без автопродления (например, бесплатный доступ)
     * перестаёт действовать после даты окончания, даже если флаг активности не сняли.
     */
    public function grantsAccessNow(): bool
    {
        if (! $this->active) {
            return false;
        }

        return $this->auto_pay || $this->until === null || $this->until->gte(today());
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
