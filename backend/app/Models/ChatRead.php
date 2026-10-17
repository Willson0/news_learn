<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class ChatRead extends Model
{
    protected $fillable = ['chat_id', 'user_id', 'last_read_message_id'];

    protected $casts = [
        'last_read_message_id' => 'integer',
    ];
}
