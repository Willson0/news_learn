<?php

use App\Http\Middleware\EnsureAdmin;
use App\Http\Middleware\SecurityHeaders;
use App\Http\Middleware\TouchLastActive;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // nginx/Caddy на хосте терминирует HTTPS: доверяем X-Forwarded-Proto, иначе подписанные
        // ссылки на вложения строятся как http, а iOS их не грузит (белые фото в чате).
        $middleware->trustProxies(at: '*');

        $middleware->append(SecurityHeaders::class);
        // Общий лимит запросов API (AppServiceProvider::configureRateLimiting).
        $middleware->throttleApi('api');

        $middleware->alias([
            'admin' => EnsureAdmin::class,
            'active' => TouchLastActive::class,
        ]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );
    })->create();
