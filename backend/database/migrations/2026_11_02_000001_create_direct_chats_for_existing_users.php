<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

/**
 * Личный чат с администрацией теперь создаётся вместе с аккаунтом. Заводим его и всем,
 * кто зарегистрировался раньше, чтобы админ видел их в «Личных чатах» до первого сообщения.
 */
return new class extends Migration
{
    public function up(): void
    {
        $now = now();
        DB::table('users')
            ->whereNotExists(fn ($q) => $q->select(DB::raw(1))->from('chats')
                ->whereColumn('chats.user_id', 'users.id')->where('chats.type', 'direct'))
            ->orderBy('id')
            ->select(['id', 'name', 'created_at'])
            ->chunk(500, function ($users) use ($now) {
                DB::table('chats')->insertOrIgnore($users->map(fn ($u) => [
                    'slug' => 'dm-'.$u->id,
                    'title' => $u->name,
                    'type' => 'direct',
                    'section' => 'direct',
                    'position' => 0,
                    'user_id' => $u->id,
                    'created_at' => $u->created_at ?? $now,
                    'updated_at' => $now,
                ])->all());
            });
    }

    public function down(): void
    {
        // Пустые личные чаты безвредны: откат их не удаляет.
    }
};
