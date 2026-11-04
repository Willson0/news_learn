<?php

namespace App\Models;

use App\Support\Period;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Invoice extends Model
{
    public const PENDING = 'pending';

    public const PAID = 'paid';

    public const CANCELED = 'canceled';

    protected $fillable = [
        'user_id', 'amount', 'months', 'days', 'status', 'tbank_payment_id',
        'confirmation_url', 'paid_at', 'created_by',
    ];

    protected $casts = [
        'paid_at' => 'datetime',
    ];

    /** Последний неоплаченный счёт пользователя. */
    public static function pendingFor(User $user): ?self
    {
        return self::where('user_id', $user->id)
            ->where('status', self::PENDING)
            ->latest('id')
            ->first();
    }

    /** Срок одного периода: дни, если заданы, иначе месяцы (у подписки без срока — по умолчанию из настроек). */
    public function period(): Period
    {
        return Period::of($this->months, $this->days, null);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
