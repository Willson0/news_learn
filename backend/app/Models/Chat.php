<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;

class Chat extends Model
{
    protected $fillable = [
        'slug', 'title', 'type', 'section', 'subtitle', 'pinned', 'position',
        'report_id', 'user_id', 'avatar_path',
    ];

    protected $casts = [
        'pinned' => 'boolean',
    ];

    public function messages(): HasMany
    {
        return $this->hasMany(Message::class);
    }

    public function latestMessage(): HasOne
    {
        return $this->hasOne(Message::class)->latestOfMany();
    }

    public function report(): BelongsTo
    {
        return $this->belongsTo(Report::class);
    }

    /** Владелец личного чата с админом. */
    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }
}
