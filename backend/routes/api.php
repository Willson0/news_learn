<?php

use App\Http\Controllers\AccountController;
use App\Http\Controllers\Admin;
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
    Route::post('/register/confirm', [AuthController::class, 'confirmRegistration']);
    Route::post('/register/resend', [AuthController::class, 'resendRegistrationCode']);
    Route::post('/login', [AuthController::class, 'login']);
    Route::post('/recovery', [AuthController::class, 'requestRecovery']);
    Route::post('/recovery/reset', [AuthController::class, 'resetPassword']);
});

// Публичные справочники (нужны и на экране входа).
Route::get('/instruments', [InstrumentController::class, 'index']);

// Файлы отчётов и фото чатов — без токена, чтобы открывать их в <img>/<iframe>.
Route::get('/reports/{report}/cover', [ReportController::class, 'cover']);
Route::get('/reports/{report}/html', [ReportController::class, 'html']);
Route::get('/chats/{slug}/avatar', [ChatController::class, 'avatar']);

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

    // --- Админка: только для админов (по Telegram ID) ---
    Route::middleware('admin')->prefix('admin')->group(function () {
        // Список админов: добавлять/удалять могут только админы, главного удалить нельзя.
        Route::get('/admins', [Admin\AdminController::class, 'index']);
        Route::post('/admins', [Admin\AdminController::class, 'store']);
        Route::delete('/admins/{telegramId}', [Admin\AdminController::class, 'destroy']);

        // Отчёты (multipart: обложка и html-файл, поэтому обновление — POST).
        Route::post('/reports', [Admin\ReportController::class, 'store']);
        Route::post('/reports/{report}', [Admin\ReportController::class, 'update']);
        Route::delete('/reports/{report}', [Admin\ReportController::class, 'destroy']);

        // Настройки чата: название и фото.
        Route::post('/chats/{slug}', [Admin\ChatController::class, 'update']);
    });
});
