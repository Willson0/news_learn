<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

// Базовые маршруты API. Фронтенд (Vue) живёт отдельным сервером и обращается
// сюда по префиксу /api. Конкретные ресурсы добавляются по мере работы над экранами.

Route::get('/ping', function () {
    return response()->json([
        'status' => 'ok',
        'app' => config('app.name'),
        'time' => now()->toIso8601String(),
    ]);
});

Route::middleware('auth:sanctum')->get('/user', function (Request $request) {
    return $request->user();
});
