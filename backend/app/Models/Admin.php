<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Admin extends Model
{
    protected $fillable = ['user_id', 'telegram_id', 'name', 'added_by'];

    public static function rootTelegramId(): int
    {
        return (int) config('admin.root_telegram_id');
    }

    public static function isRoot(int|string|null $telegramId): bool
    {
        return $telegramId !== null && (int) $telegramId === self::rootTelegramId();
    }

    public static function hasTelegramRow(string $telegramId): bool
    {
        return self::isRoot($telegramId) || self::where('telegram_id', $telegramId)->exists();
    }

    /** Админ — главный, либо запись привязана к этому пользователю (по users.id или Telegram ID). */
    public static function grantsAccess(?User $user): bool
    {
        if (! $user) {
            return false;
        }
        if (self::isRoot($user->telegram_id)) {
            return true;
        }

        $query = self::where('user_id', $user->id);
        if ($user->telegram_id !== null) {
            $query->orWhere('telegram_id', $user->telegram_id);
        }

        return $query->exists();
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function addedBy(): BelongsTo
    {
        return $this->belongsTo(User::class, 'added_by');
    }
}
