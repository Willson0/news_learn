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

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
