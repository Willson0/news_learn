<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('chats', function (Blueprint $table) {
            $table->id();
            $table->string('slug')->unique();                 // admin, general, gold, ...
            $table->string('title');
            $table->string('type')->default('group');          // admin|group|report
            $table->string('section')->default('main');        // admin|main|reports
            $table->string('subtitle')->nullable();            // «15 участников»
            $table->boolean('pinned')->default(false);
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('chats');
    }
};
