<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Учётки веб-админки (логин и пароль). Не связаны с Telegram-аккаунтами пользователей.
     */
    public function up(): void
    {
        Schema::create('panel_admins', function (Blueprint $table) {
            $table->id();
            $table->string('login')->unique();
            $table->string('password');
            $table->rememberToken();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('panel_admins');
    }
};
