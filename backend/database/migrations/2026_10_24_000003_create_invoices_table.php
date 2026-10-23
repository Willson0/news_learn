<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Счета, которые админ выставляет пользователю. Оплата идёт через YooKassa,
     * способ оплаты сохраняется для автопродления.
     */
    public function up(): void
    {
        Schema::create('invoices', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->unsignedInteger('amount');                  // рубли за период
            $table->unsignedSmallInteger('months');             // срок одного периода в месяцах
            $table->string('status')->default('pending');       // pending | paid | canceled
            $table->string('yookassa_payment_id')->nullable()->unique();
            $table->string('confirmation_url', 2048)->nullable();
            $table->timestamp('paid_at')->nullable();
            $table->string('created_by')->nullable();           // логин админа
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('invoices');
    }
};
