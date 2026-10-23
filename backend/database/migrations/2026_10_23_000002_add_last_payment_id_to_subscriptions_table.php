<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /** Последний оплаченный платёж подписки: повторный вебхук не продлевает подписку дважды. */
    public function up(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            $table->string('last_payment_id')->nullable()->after('payment_method');
        });
    }

    public function down(): void
    {
        Schema::table('subscriptions', function (Blueprint $table) {
            $table->dropColumn('last_payment_id');
        });
    }
};
