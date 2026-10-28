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

    /**
     * Чат отчёта: админские права только у создателя отчёта (у старых отчётов — у любого админа).
     * Личные чаты и чаты отчётов нельзя покинуть вручную.
     */
    public function canBeManagedBy(User $user): bool
    {
        if ($this->type === 'report') {
            $creatorId = $this->report?->created_by;

            return $creatorId === null ? $user->isAdmin() : $creatorId === $user->id;
        }

        return $user->isAdmin();
    }

    /** Закреплять и откреплять сообщения могут админы и создатель отчёта (в чате отчёта). */
    public function canPinBy(User $user): bool
    {
        if ($user->isAdmin()) {
            return true;
        }

        return $this->type === 'report' && $this->report?->created_by === $user->id;
    }

    /** Автор отчёта — адресат личных сообщений «/текст/» в чате отчёта. */
    public function reportAuthorId(): ?int
    {
        return $this->type === 'report' ? $this->report?->created_by : null;
    }

    /**
     * Нельзя выйти вручную: личный чат, чат отчёта (закрывается вместе с отчётом)
     * и общие чаты раздела «Чаты» — в них состоят все пользователи.
     */
    public function isPermanent(): bool
    {
        return in_array($this->type, ['direct', 'report'], true) || $this->section === 'main';
    }

    /** Владелец личного чата с админом. */
    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }
}
