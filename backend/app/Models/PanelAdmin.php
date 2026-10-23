<?php

namespace App\Models;

use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

/**
 * Учётка веб-админки: вход логином и паролем. Отдельна от пользователей приложения.
 */
class PanelAdmin extends Authenticatable
{
    use Notifiable;

    protected $fillable = ['login', 'password'];

    protected $hidden = ['password', 'remember_token'];

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'password' => 'hashed',
        ];
    }
}
