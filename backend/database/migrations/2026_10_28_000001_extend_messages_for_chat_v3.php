<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('messages', function (Blueprint $table) {
            // Служебное сообщение: join — пользователь зашёл в чат, leave — вышел.
            $table->string('event', 16)->nullable()->after('body');
            // Личное сообщение в чате отчёта (/текст/): видят только автор сообщения и этот пользователь.
            $table->foreignId('private_to_id')->nullable()->after('reply_to_id')
                ->constrained('users')->cascadeOnDelete();
            // Закрепов может быть несколько (как в Телеграме): порядок — по времени закрепления.
            $table->timestamp('pinned_at')->nullable()->after('pinned');
        });

        // «Удалить у меня»: сообщение скрыто только для этого пользователя (на всех его устройствах).
        Schema::create('message_hides', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('message_id')->constrained()->cascadeOnDelete();
            $table->timestamps();

            $table->unique(['user_id', 'message_id']);
        });

        // Ссылка на график может быть и кодом виджета TradingView — он длиннее 255 символов.
        Schema::table('reports', function (Blueprint $table) {
            $table->text('chart_url')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('message_hides');

        Schema::table('messages', function (Blueprint $table) {
            $table->dropConstrainedForeignId('private_to_id');
            $table->dropColumn(['event', 'pinned_at']);
        });
    }
};
