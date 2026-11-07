<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Когда пользователям ушёл push о новом отчёте: уведомление отправляется один раз —
     * при публикации (сразу при создании или когда черновик становится актуальным).
     */
    public function up(): void
    {
        Schema::table('reports', function (Blueprint $table) {
            $table->timestamp('notified_at')->nullable()->after('published_at');
        });

        // Уже опубликованные отчёты считаем разосланными, чтобы правка статуса не прислала старый отчёт.
        DB::table('reports')->where('status', '!=', 'draft')->update(['notified_at' => DB::raw('created_at')]);
    }

    public function down(): void
    {
        Schema::table('reports', function (Blueprint $table) {
            $table->dropColumn('notified_at');
        });
    }
};
