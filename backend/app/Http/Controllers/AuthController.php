<?php

namespace App\Http\Controllers;

use App\Models\PasswordResetCode;
use App\Models\User;
use App\Services\TelegramInitData;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
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
     * Регистрация по email, телефону и паролю (форма из макета).
     */
    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email', 'max:255', Rule::unique('users', 'email')],
            'phone' => ['required', 'string', 'max:32'],
            'password' => ['required', 'string', 'min:8'],
            'name' => ['nullable', 'string', 'max:255'],
        ]);

        $user = User::create([
            'name' => $data['name'] ?? Str::before($data['email'], '@'),
            'email' => $data['email'],
            'phone' => $data['phone'],
            'password' => $data['password'],
        ]);

        return $this->tokenResponse($user, 201);
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

        // TODO: подключить реальную отправку письма. Пока пишем в лог.
        Log::info('Временный код восстановления', ['email' => $data['email'], 'code' => $code]);

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
