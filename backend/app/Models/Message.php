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
        'chat_id', 'user_id', 'reply_to_id', 'private_to_id', 'private_owner_id', 'author_name', 'author_role', 'body',
        'client_id', 'event', 'pinned', 'pinned_at',
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

    /** Личное сообщение «/текст/» и ответы на него (любой глубины). */
    public function isPrivate(): bool
    {
        return $this->private_owner_id !== null;
    }

    /**
     * Видно ли сообщение пользователю: личную ветку видят её владелец и staff чата
     * (админы, создатель отчёта) — см. Chat::isStaff.
     */
    public function isVisibleTo(User $user, bool $staff = false): bool
    {
        return ! $this->isPrivate() || $staff || $this->private_owner_id === $user->id;
    }

    /**
     * Сообщения, которые видит пользователь: без чужих личных веток (если он не staff чата)
     * и без скрытых им («удалить у меня»).
     */
    public function scopeVisibleTo(Builder $query, User $user, bool $staff = false): Builder
    {
        if (! $staff) {
            $query->where(function (Builder $q) use ($user) {
                $q->whereNull('private_owner_id')->orWhere('private_owner_id', $user->id);
            });
        }

        return $query->whereNotIn('id', DB::table('message_hides')->where('user_id', $user->id)->select('message_id'));
    }

    public function privateOwner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'private_owner_id');
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
