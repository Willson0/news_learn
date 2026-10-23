<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Успешные оплаты подписки: счета админа и автосписания. Основа аналитики продаж.
     */
    public function up(): void
    {
        Schema::create('payments', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('invoice_id')->nullable()->constrained()->nullOnDelete();
            $table->string('kind');                              // invoice | renewal
            $table->unsignedInteger('amount');
            $table->unsignedSmallInteger('months');
            $table->string('yookassa_payment_id')->nullable()->unique();
            $table->timestamp('paid_at')->index();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('payments');
    }
};
