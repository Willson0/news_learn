<?php

namespace App\Models;

use App\Support\Period;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Payment extends Model
{
    protected $fillable = [
        'user_id', 'invoice_id', 'kind', 'amount', 'months', 'days', 'tbank_payment_id', 'paid_at',
    ];

    protected $casts = [
        'paid_at' => 'datetime',
    ];

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
