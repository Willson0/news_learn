<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Report extends Model
{
    protected $fillable = [
        'instrument_id', 'title', 'description', 'body',
        'badge', 'status', 'published_at',
    ];

    protected $casts = [
        'published_at' => 'date',
    ];

    public function instrument(): BelongsTo
    {
        return $this->belongsTo(Instrument::class);
    }
}
