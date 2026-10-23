<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

// Админом можно назначить любого пользователя по тегу. У пользователя может не быть Telegram ID,
// поэтому права привязываем к users.id, а telegram_id оставляем необязательным.
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('admins', function (Blueprint $table) {
            $table->unsignedBigInteger('telegram_id')->nullable()->change();
            $table->foreignId('user_id')->nullable()->unique()->after('id')->constrained('users')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('admins', function (Blueprint $table) {
            $table->dropConstrainedForeignId('user_id');
        });
    }
};
