<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

// Переход с ЮKassa на Т-Кассу. Идентификаторы ЮKassa (способы оплаты и платежи) в Т-Кассе не работают,
// поэтому сохранённые карты очищаются: пользователям нужно привязать карту заново.
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->renameColumn('yookassa_payment_method_id', 'tbank_rebill_id');
        });
        Schema::table('invoices', function (Blueprint $table) {
            $table->renameColumn('yookassa_payment_id', 'tbank_payment_id');
        });
        Schema::table('payments', function (Blueprint $table) {
            $table->renameColumn('yookassa_payment_id', 'tbank_payment_id');
        });

        DB::table('users')->update([
            'tbank_rebill_id' => null,
            'card_brand' => null,
            'card_last4' => null,
            'card_bound_at' => null,
        ]);
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->renameColumn('tbank_rebill_id', 'yookassa_payment_method_id');
        });
        Schema::table('invoices', function (Blueprint $table) {
            $table->renameColumn('tbank_payment_id', 'yookassa_payment_id');
        });
        Schema::table('payments', function (Blueprint $table) {
            $table->renameColumn('tbank_payment_id', 'yookassa_payment_id');
        });
    }
};
