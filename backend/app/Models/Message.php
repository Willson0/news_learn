<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Facades\DB;

class Message extends Model
{
    protected $fillable = [
        'chat_id', 'user_id', 'reply_to_id', 'private_to_id', 'author_name', 'author_role', 'body',
        'event', 'pinned', 'pinned_at',
    ];

    protected $casts = [
        'pinned' => 'boolean',
        'pinned_at' => 'datetime',
    ];

    /** Служебное сообщение «зашёл в чат» / «вышел из чата». */
    public function isSystem(): bool
    {
        return $this->event !== null;
    }

    /** Личное сообщение (/текст/ автору отчёта и ответы на него). */
    public function isPrivate(): bool
    {
        return $this->private_to_id !== null;
    }

    /** Видно ли сообщение пользователю: личное — только двум участникам переписки. */
    public function isVisibleTo(User $user): bool
    {
        return ! $this->isPrivate() || $this->user_id === $user->id || $this->private_to_id === $user->id;
    }

    /**
     * Сообщения, которые видит пользователь: без чужих личных и без скрытых им («удалить у меня»).
     */
    public function scopeVisibleTo(Builder $query, User $user): Builder
    {
        return $query
            ->where(function (Builder $q) use ($user) {
                $q->whereNull('private_to_id')
                    ->orWhere('user_id', $user->id)
                    ->orWhere('private_to_id', $user->id);
            })
            ->whereNotIn('id', DB::table('message_hides')->where('user_id', $user->id)->select('message_id'));
    }

    public function privateTo(): BelongsTo
    {
        return $this->belongsTo(User::class, 'private_to_id');
    }

    public function chat(): BelongsTo
    {
        return $this->belongsTo(Chat::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function replyTo(): BelongsTo
    {
        return $this->belongsTo(Message::class, 'reply_to_id');
    }

    public function attachments(): HasMany
    {
        return $this->hasMany(MessageAttachment::class);
    }

    public function reactions(): HasMany
    {
        return $this->hasMany(MessageReaction::class);
    }
}
