<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->unsignedBigInteger('telegram_id')->nullable()->unique()->after('id');
            $table->string('phone')->nullable()->after('email');
            $table->string('username')->nullable()->after('phone');
            $table->string('avatar_url')->nullable()->after('username');
            $table->boolean('notify_all')->default(true)->after('avatar_url');
            $table->boolean('notify_by_instrument')->default(false)->after('notify_all');
            // email становится необязательным: вход может быть только по Telegram
            $table->string('email')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn([
                'telegram_id', 'phone', 'username', 'avatar_url',
                'notify_all', 'notify_by_instrument',
            ]);
        });
    }
};
