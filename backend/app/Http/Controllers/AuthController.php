<?php

namespace App\Http\Controllers;

use App\Mail\CodeMail;
use App\Models\PasswordResetCode;
use App\Models\PendingRegistration;
use App\Models\User;
use App\Services\TelegramInitData;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    /**
     * Вход через Telegram Web App: проверяем подпись initData и выдаём токен.
     */
    public function telegram(Request $request, TelegramInitData $telegram): JsonResponse
    {
        $data = $request->validate([
            'init_data' => ['required', 'string'],
        ]);

        $parsed = $telegram->validate($data['init_data']);

        // Для локальной разработки разрешаем «небезопасный» разбор без проверки.
        if ($parsed === null && config('services.telegram.allow_insecure')) {
            parse_str($data['init_data'], $raw);
            if (isset($raw['user']) && is_string($raw['user'])) {
                $raw['user'] = json_decode($raw['user'], true);
            }
            $parsed = $raw;
        }

        if ($parsed === null || empty($parsed['user']['id'])) {
            throw ValidationException::withMessages([
                'init_data' => 'Не удалось проверить данные Telegram.',
            ]);
        }

        $tg = $parsed['user'];

        $user = User::firstOrNew(['telegram_id' => $tg['id']]);
        $user->name = trim(($tg['first_name'] ?? '').' '.($tg['last_name'] ?? '')) ?: ($tg['username'] ?? 'Пользователь');
        $user->username = isset($tg['username']) ? '@'.$tg['username'] : $user->username;
        $user->avatar_url = $tg['photo_url'] ?? $user->avatar_url;
        if (! $user->exists) {
            $user->password = Hash::make(Str::random(40));
        }
        $user->save();

        return $this->tokenResponse($user, 201);
    }

    /**
     * Шаг 1 регистрации: проверяем данные, сохраняем заявку и шлём код на почту.
     * Пользователь ещё не создаётся — его создаёт confirmRegistration после
     * ввода верного кода.
     */
    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email', 'max:255', Rule::unique('users', 'email')],
            'phone' => ['required', 'string', 'max:32'],
            'password' => ['required', 'string', 'min:8'],
            'name' => ['nullable', 'string', 'max:255'],
        ]);

        $code = $this->makeCode();

        // Одна активная заявка на email: перезаписываем прежнюю.
        PendingRegistration::where('email', $data['email'])->delete();
        PendingRegistration::create([
            'email' => $data['email'],
            'phone' => $data['phone'],
            'name' => $data['name'] ?? null,
            'password' => Hash::make($data['password']),
            'code' => Hash::make($code),
            'expires_at' => now()->addMinutes(15),
        ]);

        $this->sendCode($data['email'], $code, 'register');

        return response()->json([
            'status' => 'code_sent',
            'email' => $data['email'],
        ], 202);
    }

    /**
     * Шаг 2 регистрации: сверяем код из письма, создаём пользователя и выдаём токен.
     */
    public function confirmRegistration(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'code' => ['required', 'string'],
        ]);

        $pending = PendingRegistration::where('email', $data['email'])->first();

        if (! $pending || $pending->isExpired()) {
            $pending?->delete();
            throw ValidationException::withMessages([
                'code' => 'Код устарел. Запросите новый.',
            ]);
        }

        // Защита от перебора: не больше 5 неверных попыток.
        if ($pending->attempts >= 5) {
            $pending->delete();
            throw ValidationException::withMessages([
                'code' => 'Слишком много попыток. Запросите новый код.',
            ]);
        }

        if (! Hash::check($data['code'], $pending->code)) {
            $pending->increment('attempts');
            throw ValidationException::withMessages([
                'code' => 'Неверный код.',
            ]);
        }

        // На случай, если email заняли, пока ждали подтверждения.
        if (User::where('email', $pending->email)->exists()) {
            $pending->delete();
            throw ValidationException::withMessages([
                'email' => 'Пользователь с такой почтой уже существует.',
            ]);
        }

        $user = User::create([
            'name' => $pending->name ?: Str::before($pending->email, '@'),
            'email' => $pending->email,
            'phone' => $pending->phone,
            'password' => Str::random(40), // временный, перезапишем готовым хэшем ниже
        ]);

        // Пароль уже захэширован в заявке — пишем его напрямую, минуя cast 'hashed'.
        User::where('id', $user->id)->update(['password' => $pending->password]);

        $pending->delete();

        return $this->tokenResponse($user->fresh(), 201);
    }

    /**
     * Повторная отправка кода подтверждения регистрации.
     */
    public function resendRegistrationCode(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
        ]);

        $pending = PendingRegistration::where('email', $data['email'])->first();
        if (! $pending) {
            throw ValidationException::withMessages([
                'email' => 'Заявка не найдена. Начните регистрацию заново.',
            ]);
        }

        $code = $this->makeCode();
        $pending->update([
            'code' => Hash::make($code),
            'attempts' => 0,
            'expires_at' => now()->addMinutes(15),
        ]);

        $this->sendCode($data['email'], $code, 'register');

        return response()->json(['status' => 'code_sent']);
    }

    /**
     * Вход по email или телефону и паролю.
     */
    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'identifier' => ['required', 'string'],
            'password' => ['required', 'string'],
        ]);

        $field = str_contains($data['identifier'], '@') ? 'email' : 'phone';
        $user = User::where($field, $data['identifier'])->first();

        if (! $user || ! Hash::check($data['password'], (string) $user->password)) {
            throw ValidationException::withMessages([
                'identifier' => 'Неверный логин или пароль.',
            ]);
        }

        return $this->tokenResponse($user);
    }

    /**
     * Запрос временного пароля на почту (кнопка «Отправить повторно»).
     */
    public function requestRecovery(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
        ]);

        $code = (string) random_int(100000, 999999);

        PasswordResetCode::where('email', $data['email'])->delete();
        PasswordResetCode::create([
            'email' => $data['email'],
            'code' => Hash::make($code),
            'expires_at' => now()->addMinutes(15),
        ]);

        $this->sendCode($data['email'], $code, 'recovery');

        return response()->json(['status' => 'sent']);
    }

    /**
     * Установка нового пароля. Требует, чтобы ранее был запрошен код (пользователь
     * подтвердил доступ к почте), срок действия — 15 минут.
     */
    public function resetPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string', 'min:8'],
            'code' => ['nullable', 'string'],
        ]);

        $record = PasswordResetCode::where('email', $data['email'])
            ->where('expires_at', '>', now())
            ->latest()
            ->first();

        if (! $record) {
            throw ValidationException::withMessages([
                'email' => 'Сначала запросите временный пароль на почту.',
            ]);
        }

        if (! empty($data['code']) && ! Hash::check($data['code'], $record->code)) {
            throw ValidationException::withMessages([
                'code' => 'Неверный код.',
            ]);
        }

        $user = User::where('email', $data['email'])->first();
        if (! $user) {
            throw ValidationException::withMessages([
                'email' => 'Пользователь с такой почтой не найден.',
            ]);
        }

        $user->password = $data['password'];
        $user->save();

        PasswordResetCode::where('email', $data['email'])->delete();

        return $this->tokenResponse($user);
    }

    /**
     * Текущий пользователь.
     */
    public function me(Request $request): JsonResponse
    {
        return response()->json([
            'user' => $this->userPayload($request->user()),
        ]);
    }

    /**
     * Выход: удаляем текущий токен.
     */
    public function logout(Request $request): JsonResponse
    {
        $token = $request->user()->currentAccessToken();
        if ($token) {
            $token->delete();
        }

        return response()->json(['status' => 'ok']);
    }

    /**
     * Шестизначный код подтверждения.
     */
    private function makeCode(): string
    {
        return (string) random_int(100000, 999999);
    }

    /**
     * Отправка кода на почту. Если почтовый драйвер недоступен — не роняем
     * запрос (код всегда можно запросить повторно). В лог код пишем тоже,
     * чтобы можно было проверить флоу, пока не настроен реальный SMTP.
     */
    private function sendCode(string $email, string $code, string $purpose): void
    {
        try {
            Mail::to($email)->send(new CodeMail($code, $purpose));
        } catch (\Throwable $e) {
            Log::error('Не удалось отправить код на почту', [
                'email' => $email,
                'error' => $e->getMessage(),
            ]);
        }

        Log::info('Код подтверждения', ['email' => $email, 'purpose' => $purpose, 'code' => $code]);
    }

    private function tokenResponse(User $user, int $status = 200): JsonResponse
    {
        $token = $user->createToken('webapp')->plainTextToken;

        return response()->json([
            'token' => $token,
            'user' => $this->userPayload($user),
        ], $status);
    }

    /**
     * @return array<string, mixed>
     */
    private function userPayload(User $user): array
    {
        return [
            'id' => $user->id,
            'name' => $user->name,
            'email' => $user->email,
            'phone' => $user->phone,
            'username' => $user->username,
            'avatar_url' => $user->avatar_url,
            'telegram_id' => $user->telegram_id ? (string) $user->telegram_id : null,
            'is_admin' => $user->isAdmin(),
            'is_root_admin' => $user->isRootAdmin(),
        ];
    }
}
