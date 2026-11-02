<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Collection;
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

    /**
     * Личный чат пользователя с администрацией. Создаётся вместе с аккаунтом, поэтому сразу
     * виден в «Личных чатах» админа, даже пока в нём нет ни одного сообщения.
     */
    public static function directFor(User $user): self
    {
        return self::firstOrCreate(
            ['type' => 'direct', 'user_id' => $user->id],
            [
                'slug' => 'dm-'.$user->id,
                'title' => $user->name,
                'section' => 'direct',
                'position' => 0,
            ],
        );
    }

    /**
     * Личные чаты для списка администрации: все пользователи, включая тех, кто ещё не писал.
     * Сначала чаты с перепиской по последнему сообщению, затем пустые — новые аккаунты сверху.
     * Пустые чаты самих админов (если такие есть) не показываем.
     *
     * @return Collection<int, self>
     */
    public static function directChatsForStaff(): Collection
    {
        $chats = self::with(['latestMessage', 'owner'])->where('type', 'direct')->get();
        $adminIds = Admin::userIds();

        return $chats
            ->filter(fn (self $c) => $c->latestMessage !== null || ($c->owner !== null && ! in_array((int) $c->user_id, $adminIds, true)))
            ->sort(function (self $a, self $b) {
                $la = $a->latestMessage?->created_at;
                $lb = $b->latestMessage?->created_at;
                if ($la && $lb) {
                    return $lb <=> $la;
                }
                if ($la || $lb) {
                    return $la ? -1 : 1;
                }

                return [$b->created_at, $b->id] <=> [$a->created_at, $a->id];
            })
            ->values();
    }

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

    /**
     * Staff чата видит все личные ветки «/текст/» и отвечает в них: админы и создатель
     * отчёта в чате отчёта.
     */
    public function isStaff(User $user): bool
    {
        return $user->isAdmin() || $this->canBeManagedBy($user);
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
