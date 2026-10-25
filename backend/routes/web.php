<?php

use App\Http\Controllers\Admin;
use App\Http\Controllers\InstrumentController;
use App\Http\Controllers\Panel;
use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return view('welcome');
});

/*
|--------------------------------------------------------------------------
| Веб-админка (/admin)
|--------------------------------------------------------------------------
| Одностраничное приложение на Vue 3 (Options API). Все данные идут через
| /admin/api/* с сессией: вход логином и паролем, отдельно от пользователей.
*/
Route::prefix('admin')->group(function () {
    Route::prefix('api')->group(function () {
        Route::post('/login', [Panel\AuthController::class, 'login'])->middleware('throttle:panel-login');

        Route::middleware('auth:panel')->group(function () {
            Route::post('/logout', [Panel\AuthController::class, 'logout']);
            Route::get('/me', [Panel\AuthController::class, 'me']);
            Route::put('/account', [Panel\AuthController::class, 'update']);

            // Пользователи и счета.
            Route::get('/users', [Panel\UserController::class, 'index']);
            Route::get('/users/{user}', [Panel\UserController::class, 'show']);
            Route::post('/users/{user}/invoices', [Panel\UserController::class, 'invoice']);
            Route::put('/users/{user}/subscription', [Panel\UserController::class, 'updateSubscription']);
            Route::post('/users/{user}/subscription/grant', [Panel\UserController::class, 'grantFree']);
            Route::get('/invoices', [Panel\InvoiceController::class, 'index']);

            // Аналитика.
            Route::get('/analytics/sales', [Panel\AnalyticsController::class, 'sales']);
            Route::get('/analytics/stats', [Panel\AnalyticsController::class, 'stats']);

            // Отчёты: список, публикация, статус, правки.
            Route::get('/reports', [Panel\ReportController::class, 'index']);
            Route::post('/reports', [Panel\ReportController::class, 'store']);
            Route::post('/reports/{report}', [Panel\ReportController::class, 'update']);
            Route::post('/reports/{report}/status', [Panel\ReportController::class, 'status']);
            Route::delete('/reports/{report}', [Panel\ReportController::class, 'destroy']);

            // Чаты: общие, личные (с ответами администратора).
            Route::get('/chats/groups', [Panel\ChatController::class, 'groups']);
            Route::get('/chats/direct', [Panel\ChatController::class, 'direct']);
            Route::get('/chats/{slug}/messages', [Panel\ChatController::class, 'messages']);
            Route::post('/chats/{slug}/messages', [Panel\ChatController::class, 'send']);

            // Справочник инструментов для отчётов.
            Route::get('/instruments', [InstrumentController::class, 'index']);

            // Рассылка push-уведомлений.
            Route::post('/push', [Panel\PushController::class, 'broadcast']);

            // Настройки и администраторы (по Telegram ID).
            Route::get('/settings', [Panel\SettingsController::class, 'show']);
            Route::put('/settings', [Panel\SettingsController::class, 'update']);
            Route::get('/admins', [Admin\AdminController::class, 'index']);
            Route::post('/admins', [Admin\AdminController::class, 'store']);
            Route::delete('/admins/{telegramId}', [Admin\AdminController::class, 'destroy']);
        });
    });

    // Все остальные адреса /admin отдают оболочку приложения, маршрутизацию делает Vue.
    Route::get('/{any?}', fn () => view('admin.app'))->where('any', '^(?!api(/|$)).*$');
});
