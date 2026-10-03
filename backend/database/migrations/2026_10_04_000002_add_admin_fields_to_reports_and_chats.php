<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('reports', function (Blueprint $table) {
            $table->string('chart_url')->nullable()->after('body');   // ссылка на график
            $table->string('cover_path')->nullable()->after('chart_url');
            $table->string('cover_name')->nullable()->after('cover_path');
            $table->string('html_path')->nullable()->after('cover_name');
            $table->string('html_name')->nullable()->after('html_path');
        });

        Schema::table('chats', function (Blueprint $table) {
            // Чат отчёта удаляется вместе с отчётом.
            $table->foreignId('report_id')->nullable()->after('id')->constrained()->cascadeOnDelete();
            // Владелец личного чата с админом (type = direct).
            $table->foreignId('user_id')->nullable()->after('report_id')->constrained()->cascadeOnDelete();
            $table->string('avatar_path')->nullable()->after('subtitle');
        });
    }

    public function down(): void
    {
        Schema::table('chats', function (Blueprint $table) {
            $table->dropConstrainedForeignId('report_id');
            $table->dropConstrainedForeignId('user_id');
            $table->dropColumn('avatar_path');
        });

        Schema::table('reports', function (Blueprint $table) {
            $table->dropColumn(['chart_url', 'cover_path', 'cover_name', 'html_path', 'html_name']);
        });
    }
};
