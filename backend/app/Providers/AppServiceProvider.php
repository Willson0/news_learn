<?php

namespace App\Providers;

use App\Models\AppSetting;
use App\Services\TelegramInitData;
use Carbon\Carbon;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        // Подпись initData проверяется токеном бота; без привязки сюда приходил null и проверка всегда падала.
        $this->app->bind(TelegramInitData::class, fn () => new TelegramInitData(config('services.telegram.bot_token')));
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        Carbon::setLocale('ru');

        $this->configureRateLimiting();

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

    /**
     * Ограничения частоты запросов: защита от перебора паролей и кодов и от рассылки писем.
     */
    private function configureRateLimiting(): void
    {
        $tooMany = fn () => response()->json(['message' => 'Слишком много попыток. Попробуйте позже.'], 429);

        // Вход в приложение: по логину и по адресу.
        RateLimiter::for('login', fn (Request $request) => [
            Limit::perMinute(5)->by('login:'.mb_strtolower((string) $request->input('identifier')).'|'.$request->ip())->response($tooMany),
            Limit::perHour(30)->by('login-id:'.mb_strtolower((string) $request->input('identifier')))->response($tooMany),
            Limit::perMinute(20)->by('login-ip:'.$request->ip())->response($tooMany),
        ]);

        // Коды на почту (регистрация, восстановление): не больше 3 в минуту и 10 в час на адрес.
        RateLimiter::for('codes', fn (Request $request) => [
            Limit::perMinute(3)->by('codes:'.mb_strtolower((string) $request->input('email')))->response($tooMany),
            Limit::perHour(10)->by('codes-h:'.mb_strtolower((string) $request->input('email')))->response($tooMany),
            Limit::perMinute(10)->by('codes-ip:'.$request->ip())->response($tooMany),
        ]);

        // Ввод кода и прочие запросы авторизации без токена.
        RateLimiter::for('auth', fn (Request $request) => [
            Limit::perMinute(10)->by('auth:'.mb_strtolower((string) $request->input('email')).'|'.$request->ip())->response($tooMany),
            Limit::perMinute(30)->by('auth-ip:'.$request->ip())->response($tooMany),
        ]);

        // Вход в веб-админку.
        RateLimiter::for('panel-login', fn (Request $request) => [
            Limit::perMinute(5)->by('panel:'.mb_strtolower((string) $request->input('login')).'|'.$request->ip())->response($tooMany),
            Limit::perHour(20)->by('panel-login:'.mb_strtolower((string) $request->input('login')))->response($tooMany),
        ]);

        // Обычные запросы API: по пользователю, а без токена по адресу.
        RateLimiter::for('api', fn (Request $request) => Limit::perMinute(240)
            ->by($request->user()?->id ? 'user:'.$request->user()->id : 'ip:'.$request->ip())
            ->response($tooMany));

        // Действия, которые создают данные: сообщения, загрузки, платежи.
        RateLimiter::for('writes', fn (Request $request) => Limit::perMinute(30)
            ->by('writes:'.($request->user()?->id ?? $request->ip()))
            ->response($tooMany));
    }
}
