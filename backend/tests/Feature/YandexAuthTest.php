<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class YandexAuthTest extends TestCase
{
    use RefreshDatabase;

    private function challenge(string $verifier): string
    {
        return rtrim(strtr(base64_encode(hash('sha256', $verifier, true)), '+/', '-_'), '=');
    }

    /** Проходит вход до возврата в приложение и возвращает одноразовый code. */
    private function codeFor(string $verifier): string
    {
        $start = $this->get('/api/auth/yandex/start?challenge='.$this->challenge($verifier));
        parse_str((string) parse_url((string) $start->headers->get('Location'), PHP_URL_QUERY), $q);

        Http::fake([
            'oauth.yandex.ru/token' => Http::response(['access_token' => 'ya-token']),
            'login.yandex.ru/info*' => Http::response(['id' => '42', 'default_email' => 'ya@example.com', 'display_name' => 'Яндекс Пользователь']),
        ]);
        $callback = $this->get('/api/auth/yandex/callback?code=ya-code&state='.$q['state']);
        $location = (string) $callback->headers->get('Location');
        $this->assertStringStartsWith('karta://auth?code=', $location);
        $this->assertStringNotContainsString('token=', $location);
        parse_str((string) parse_url($location, PHP_URL_QUERY), $app);

        return $app['code'];
    }

    public function test_code_is_exchanged_for_token_with_matching_verifier_once(): void
    {
        $verifier = str_repeat('v', 50);
        $code = $this->codeFor($verifier);

        $token = $this->postJson('/api/auth/yandex/exchange', ['code' => $code, 'verifier' => $verifier])
            ->assertOk()->json('token');
        $this->assertNotEmpty($token);
        $this->withToken($token)->getJson('/api/user')->assertOk()->assertJsonPath('user.email', 'ya@example.com');

        // Повторно тот же code не работает.
        $this->postJson('/api/auth/yandex/exchange', ['code' => $code, 'verifier' => $verifier])->assertStatus(422);
    }

    public function test_code_is_useless_without_the_right_verifier(): void
    {
        $code = $this->codeFor(str_repeat('v', 50));

        $this->postJson('/api/auth/yandex/exchange', ['code' => $code, 'verifier' => str_repeat('x', 50)])->assertStatus(422);
    }

    public function test_start_without_challenge_is_refused(): void
    {
        $this->get('/api/auth/yandex/start')->assertRedirect();
        $this->assertStringContainsString('error=', (string) $this->get('/api/auth/yandex/start')->headers->get('Location'));
    }
}
