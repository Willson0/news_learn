<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            // Личная ветка «/текст/»: участник, который её начал. Видят он, админы и создатель
            // отчёта (в чате отчёта); ответы на любую глубину остаются в этой ветке.
            $table->foreignId('private_owner_id')->nullable()->after('private_to_id')
                ->constrained('users')->cascadeOnDelete();
        });

        // Старые личные сообщения автору отчёта: владелец ветки — тот, кто не автор отчёта.
        DB::table('messages')->whereNotNull('private_to_id')->orderBy('id')->get(['id', 'chat_id', 'user_id', 'private_to_id'])
            ->each(function ($m) {
                $authorId = DB::table('chats')->join('reports', 'reports.id', '=', 'chats.report_id')
                    ->where('chats.id', $m->chat_id)->value('reports.created_by');
                $owner = $m->user_id !== null && (int) $m->user_id === (int) $authorId ? $m->private_to_id : $m->user_id;
                DB::table('messages')->where('id', $m->id)->update(['private_owner_id' => $owner]);
            });
    }

    public function down(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            $table->dropConstrainedForeignId('private_owner_id');
        });
    }
};
