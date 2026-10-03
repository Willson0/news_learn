<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasOne;

class Report extends Model
{
    protected $fillable = [
        'instrument_id', 'title', 'description', 'body',
        'badge', 'status', 'published_at',
        'chart_url', 'cover_path', 'cover_name', 'html_path', 'html_name',
    ];

    protected $casts = [
        'published_at' => 'date',
    ];

    public function instrument(): BelongsTo
    {
        return $this->belongsTo(Instrument::class);
    }

    public function chat(): HasOne
    {
        return $this->hasOne(Chat::class);
    }
}
