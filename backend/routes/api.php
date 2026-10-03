<?php

use App\Http\Controllers\AccountController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\ChatController;
use App\Http\Controllers\InstrumentController;
use App\Http\Controllers\ProfileController;
use App\Http\Controllers\ReportController;
use App\Http\Controllers\SubscriptionController;
use Illuminate\Support\Facades\Route;

// Проверка доступности API.
Route::get('/ping', function () {
    return response()->json([
        'status' => 'ok',
        'app' => config('app.name'),
        'time' => now()->toIso8601String(),
    ]);
});

// --- Авторизация (без токена) ---
Route::prefix('auth')->group(function () {
    Route::post('/telegram', [AuthController::class, 'telegram']);
    Route::post('/register', [AuthController::class, 'register']);
    Route::post('/login', [AuthController::class, 'login']);
    Route::post('/recovery', [AuthController::class, 'requestRecovery']);
    Route::post('/recovery/reset', [AuthController::class, 'resetPassword']);
});

// Публичные справочники (нужны и на экране входа).
Route::get('/instruments', [InstrumentController::class, 'index']);

// --- Требуют токен Sanctum ---
Route::middleware('auth:sanctum')->group(function () {
    Route::get('/user', [AuthController::class, 'me']);
    Route::post('/auth/logout', [AuthController::class, 'logout']);

    // Отчёты / материалы (главная, аналитика, детальный отчёт).
    Route::get('/reports', [ReportController::class, 'index']);
    Route::get('/reports/{report}', [ReportController::class, 'show']);

    // Профиль.
    Route::get('/profile', [ProfileController::class, 'show']);
    Route::put('/profile', [ProfileController::class, 'update']);
    Route::put('/profile/instruments', [ProfileController::class, 'syncInstruments']);
    Route::put('/profile/notifications', [ProfileController::class, 'updateNotifications']);

    // Подписка.
    Route::get('/subscription', [SubscriptionController::class, 'show']);
    Route::put('/subscription', [SubscriptionController::class, 'update']);

    // Данные аккаунта.
    Route::put('/account/email', [AccountController::class, 'changeEmail']);
    Route::put('/account/phone', [AccountController::class, 'changePhone']);
    Route::put('/account/password', [AccountController::class, 'changePassword']);

    // Сообщество.
    Route::get('/chats', [ChatController::class, 'index']);
    Route::get('/chats/{slug}/messages', [ChatController::class, 'messages']);
    Route::post('/chats/{slug}/messages', [ChatController::class, 'send']);
});
