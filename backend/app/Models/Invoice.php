<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Invoice extends Model
{
    public const PENDING = 'pending';

    public const PAID = 'paid';

    public const CANCELED = 'canceled';

    protected $fillable = [
        'user_id', 'amount', 'months', 'status', 'tbank_payment_id',
        'confirmation_url', 'paid_at', 'created_by',
    ];

    protected $casts = [
        'paid_at' => 'datetime',
    ];

    /** Последний неоплаченный счёт пользователя со ссылкой на оплату. */
    public static function pendingFor(User $user): ?self
    {
        return self::where('user_id', $user->id)
            ->where('status', self::PENDING)
            ->whereNotNull('confirmation_url')
            ->latest('id')
            ->first();
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
