<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Срок периода в днях: админ может выставить счёт на произвольное число дней вместо 1/3/6/12 месяцев.
 * Если days заполнено, срок считается в днях (months при этом 0 в счетах и платежах).
 */
return new class extends Migration
{
    public function up(): void
    {
        foreach (['subscriptions', 'invoices', 'payments'] as $table) {
            Schema::table($table, function (Blueprint $t) {
                $t->unsignedSmallInteger('days')->nullable()->after('months');
            });
        }
    }

    public function down(): void
    {
        foreach (['subscriptions', 'invoices', 'payments'] as $table) {
            Schema::table($table, function (Blueprint $t) {
                $t->dropColumn('days');
            });
        }
    }
};
