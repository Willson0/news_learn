<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Admin extends Model
{
    protected $fillable = ['telegram_id', 'name', 'added_by'];

    public static function rootTelegramId(): int
    {
        return (int) config('admin.root_telegram_id');
    }

    public static function isRoot(int|string|null $telegramId): bool
    {
        return $telegramId !== null && (int) $telegramId === self::rootTelegramId();
    }

    public static function hasTelegramId(int|string|null $telegramId): bool
    {
        if ($telegramId === null || $telegramId === '') {
            return false;
        }

        return self::isRoot($telegramId) || self::where('telegram_id', $telegramId)->exists();
    }

    public function addedBy(): BelongsTo
    {
        return $this->belongsTo(User::class, 'added_by');
    }
}
