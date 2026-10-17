<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('message_attachments', function (Blueprint $table) {
            $table->id();
            $table->foreignId('message_id')->constrained()->cascadeOnDelete();
            $table->string('kind');                       // image | file | voice
            $table->string('path');                       // путь в storage/app
            $table->string('name')->nullable();           // оригинальное имя файла
            $table->string('mime')->nullable();
            $table->unsignedBigInteger('size')->nullable(); // байты
            $table->unsignedInteger('duration')->nullable(); // секунды (голос)
            $table->unsignedInteger('width')->nullable();
            $table->unsignedInteger('height')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('message_attachments');
    }
};
