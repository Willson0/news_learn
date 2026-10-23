<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Индивидуальные условия подписки, которые задаёт админ: сумма и срок периода.
     * Если поля пустые, используется цена по умолчанию из настроек.
     */
    public function up(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            $table->unsignedInteger('amount')->nullable()->after('plan');
            $table->unsignedSmallInteger('months')->nullable()->after('amount');
        });
    }

    public function down(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            $table->dropColumn(['amount', 'months']);
        });
    }
};
