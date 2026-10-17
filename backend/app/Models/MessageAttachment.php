<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class MessageAttachment extends Model
{
    protected $fillable = [
        'message_id', 'kind', 'path', 'name', 'mime', 'size', 'duration', 'width', 'height',
    ];

    protected $casts = [
        'size' => 'integer',
        'duration' => 'integer',
        'width' => 'integer',
        'height' => 'integer',
    ];

    public function message(): BelongsTo
    {
        return $this->belongsTo(Message::class);
    }
}
