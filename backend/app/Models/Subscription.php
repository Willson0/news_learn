<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Subscription extends Model
{
    protected $fillable = [
        'user_id', 'active', 'plan', 'until', 'auto_pay', 'payment_method',
    ];

    protected $casts = [
        'active' => 'boolean',
        'auto_pay' => 'boolean',
        'until' => 'date',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
