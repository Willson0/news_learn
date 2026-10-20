<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

// Привязка карты через YooKassa. Сохранённый способ оплаты хранится у пользователя:
// для списаний нужен только id способа оплаты и маска карты для показа в приложении.
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('yookassa_payment_method_id')->nullable()->after('yandex_id');
            $table->string('card_brand', 32)->nullable()->after('yookassa_payment_method_id');
            $table->string('card_last4', 4)->nullable()->after('card_brand');
            $table->timestamp('card_bound_at')->nullable()->after('card_last4');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['yookassa_payment_method_id', 'card_brand', 'card_last4', 'card_bound_at']);
        });
    }
};
