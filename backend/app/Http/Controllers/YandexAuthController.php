<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpFoundation\Response;

/**
 * Вход через Яндекс (OAuth). Поток одинаковый для iOS и Android:
 * приложение создаёт случайный verifier и открывает /auth/yandex/start?challenge=<SHA-256 verifier>
 * во встроенном браузере, сервер ведёт пользователя на Яндекс, после подтверждения возвращает его
 * на /auth/yandex/callback и перенаправляет в приложение на karta://auth?code=… (или ?error=…).
 * Приложение меняет одноразовый code на токен (POST /auth/yandex/exchange) вместе с verifier.
 * Токен не попадает в адресную строку, а чужая ссылка karta://auth не войдёт в чужой аккаунт:
 * без verifier, который знает только запустившее вход приложение, code бесполезен.
 */
class YandexAuthController extends Controller
{
    private const AUTHORIZE_URL = 'https://oauth.yandex.ru/authorize';
    private const TOKEN_URL = 'https://oauth.yandex.ru/token';
    private const INFO_URL = 'https://login.yandex.ru/info';

    public function start(Request $request): RedirectResponse
    {
        $challenge = (string) $request->query('challenge', '');
        if (! preg_match('/^[A-Za-z0-9_-]{43}$/', $challenge)) {
            return $this->toApp(['error' => 'Обновите приложение, чтобы войти через Яндекс']);
        }

        $state = Str::random(40);
        Cache::put($this->stateKey($state), $challenge, now()->addMinutes(10));

        $query = http_build_query([
            'response_type' => 'code',
            'client_id' => config('services.yandex.client_id'),
            'redirect_uri' => config('services.yandex.redirect_uri'),
            'state' => $state,
            'force_confirm' => 'yes',
        ]);

        return redirect(self::AUTHORIZE_URL.'?'.$query);
    }

    public function callback(Request $request): RedirectResponse
    {
        if ($request->filled('error')) {
            return $this->toApp(['error' => 'Вход через Яндекс отменён']);
        }

        $state = (string) $request->query('state', '');
        $challenge = $state === '' ? null : Cache::pull($this->stateKey($state));
        if (! is_string($challenge) || $challenge === '') {
            return $this->toApp(['error' => 'Сессия входа устарела, попробуйте ещё раз']);
        }

        $code = (string) $request->query('code', '');
        if ($code === '') {
            return $this->toApp(['error' => 'Яндекс не вернул код входа']);
        }

        $tokenResponse = Http::asForm()->timeout(15)->post(self::TOKEN_URL, [
            'grant_type' => 'authorization_code',
            'code' => $code,
            'client_id' => config('services.yandex.client_id'),
            'client_secret' => config('services.yandex.client_secret'),
        ]);
        $accessToken = $tokenResponse->json('access_token');
        if (! $tokenResponse->successful() || ! is_string($accessToken) || $accessToken === '') {
            return $this->toApp(['error' => 'Не удалось подтвердить вход в Яндексе']);
        }

        $info = Http::withHeaders(['Authorization' => 'OAuth '.$accessToken])
            ->timeout(15)
            ->get(self::INFO_URL, ['format' => 'json'])
            ->json();
        if (! is_array($info) || empty($info['id'])) {
            return $this->toApp(['error' => 'Не удалось получить данные профиля Яндекса']);
        }

        $user = $this->findOrCreateUser($info);

        // Одноразовый код на 2 минуты; в кэше храним только его хэш.
        $code = Str::random(64);
        Cache::put($this->codeKey($code), ['user_id' => $user->id, 'challenge' => $challenge], now()->addMinutes(2));

        return $this->toApp(['code' => $code]);
    }

    /**
     * Обмен одноразового кода на токен. verifier должен давать тот challenge, с которым начат вход.
     */
    public function exchange(Request $request): JsonResponse
    {
        $data = $request->validate([
            'code' => ['required', 'string', 'max:128'],
            'verifier' => ['required', 'string', 'min:43', 'max:128'],
        ]);

        $entry = Cache::pull($this->codeKey($data['code']));
        $expected = is_array($entry) ? (string) ($entry['challenge'] ?? '') : '';
        $actual = rtrim(strtr(base64_encode(hash('sha256', $data['verifier'], true)), '+/', '-_'), '=');
        if ($expected === '' || ! hash_equals($expected, $actual)) {
            throw ValidationException::withMessages(['code' => 'Вход через Яндекс устарел, попробуйте ещё раз']);
        }

        $user = User::findOrFail((int) $entry['user_id']);

        return response()->json(['token' => $user->createToken('webapp')->plainTextToken]);
    }

    /**
     * @param  array<string, mixed>  $info
     */
    private function findOrCreateUser(array $info): User
    {
        $yandexId = (string) $info['id'];
        $email = isset($info['default_email']) && is_string($info['default_email']) ? mb_strtolower($info['default_email']) : null;

        $user = User::where('yandex_id', $yandexId)->first();
        if (! $user && $email) {
            // Пользователь с таким же подтверждённым email в Яндексе — привязываем аккаунт.
            $user = User::where('email', $email)->first();
        }
        $isNew = ! $user;
        $user ??= new User;

        $name = $info['display_name'] ?? $info['real_name'] ?? $info['login'] ?? null;
        if (is_string($name) && $name !== '') {
            $user->name = $name;
        } elseif (! $user->exists) {
            $user->name = 'Пользователь';
        }

        $user->yandex_id = $yandexId;
        if ($email && ! $user->email) {
            $user->email = $email;
        }
        if (! $user->avatar_url && ! empty($info['default_avatar_id']) && ! ($info['is_avatar_empty'] ?? false)) {
            $user->avatar_url = 'https://avatars.yandex.net/get-yapic/'.$info['default_avatar_id'].'/islands-200';
        }
        if ($isNew) {
            $user->password = Hash::make(Str::random(40));
        }
        $user->save();

        return $user;
    }

    /**
     * @param  array<string, string>  $params
     */
    private function toApp(array $params): RedirectResponse
    {
        $scheme = config('services.yandex.app_scheme');

        return redirect($scheme.'://auth?'.http_build_query($params), Response::HTTP_FOUND);
    }

    private function stateKey(string $state): string
    {
        return 'yandex_oauth_state:'.$state;
    }

    private function codeKey(string $code): string
    {
        return 'yandex_oauth_code:'.hash('sha256', $code);
    }
}
