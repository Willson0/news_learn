<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            // Ключ отправки от приложения: повтор после обрыва сети не создаёт дубль.
            $table->string('client_id', 64)->nullable()->after('body');
            $table->unique(['chat_id', 'user_id', 'client_id']);
        });
    }

    public function down(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            $table->dropUnique(['chat_id', 'user_id', 'client_id']);
            $table->dropColumn('client_id');
        });
    }
};
