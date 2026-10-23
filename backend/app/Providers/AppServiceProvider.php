<?php

namespace App\Providers;

use App\Models\AppSetting;
use Carbon\Carbon;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        //
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        Carbon::setLocale('ru');

        // Ключ почтового сервиса, заданный в настройках админки, важнее .env.
        try {
            $emailKey = AppSetting::get('email_api_key');
            if (is_string($emailKey) && $emailKey !== '') {
                config(['services.resend.key' => $emailKey]);
            }
        } catch (\Throwable) {
            // Таблица ещё не создана (до миграций) — используем значения из .env.
        }
    }
}
