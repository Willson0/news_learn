<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    protected $fillable = [
        'name',
        'email',
        'password',
        'telegram_id',
        'phone',
        'username',
        'avatar_url',
        'avatar_path',
        'last_active_at',
        'notify_all',
        'notify_by_instrument',
    ];

    protected $hidden = [
        'password',
        'remember_token',
    ];

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'last_active_at' => 'datetime',
            'password' => 'hashed',
            'notify_all' => 'boolean',
            'notify_by_instrument' => 'boolean',
            'card_bound_at' => 'datetime',
        ];
    }

    /**
     * Админ определяется по Telegram ID: главный (из config/admin.php) или из таблицы admins.
     */
    public function isAdmin(): bool
    {
        return Admin::grantsAccess($this);
    }

    public function isRootAdmin(): bool
    {
        return Admin::isRoot($this->telegram_id);
    }

    /** Доступ к платным материалам: админ или активная подписка. Проверяется на сервере, не только в приложении. */
    public function hasAccess(): bool
    {
        return $this->isAdmin() || (bool) $this->subscription?->grantsAccessNow();
    }

    public function instruments(): BelongsToMany
    {
        return $this->belongsToMany(Instrument::class)->withTimestamps();
    }

    public function deviceTokens(): HasMany
    {
        return $this->hasMany(DeviceToken::class);
    }

    public function subscription(): HasOne
    {
        return $this->hasOne(Subscription::class);
    }

    /** Чаты, из которых пользователь вышел (скрыты от него и закрыты для записи). */
    public function leftChats(): BelongsToMany
    {
        return $this->belongsToMany(Chat::class, 'chat_leaves')->withTimestamps();
    }
}
