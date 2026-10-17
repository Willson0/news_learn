<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            // Ответ на сообщение (цитата).
            $table->foreignId('reply_to_id')->nullable()->after('user_id')
                ->constrained('messages')->nullOnDelete();
            // Закреплённое сообщение чата.
            $table->boolean('pinned')->default(false)->after('body');
            // Теперь текст необязателен — есть сообщения только с вложением.
            $table->text('body')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            $table->dropConstrainedForeignId('reply_to_id');
            $table->dropColumn('pinned');
        });
    }
};
