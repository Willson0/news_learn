<?php

namespace Tests\Feature;

use App\Mail\CodeMail;
use App\Models\User;
use App\Services\TelegramInitData;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    public function test_register_sends_code_and_defers_user(): void
    {
        Mail::fake();

        $this->postJson('/api/auth/register', [
            'email' => 'new@example.com',
            'phone' => '+70000000000',
            'password' => 'secret123',
        ])->assertStatus(202)->assertJson(['status' => 'code_sent']);

        // Пользователь ещё не создан — только заявка.
        $this->assertDatabaseMissing('users', ['email' => 'new@example.com']);
        $this->assertDatabaseHas('pending_registrations', ['email' => 'new@example.com']);
        Mail::assertSent(CodeMail::class);
    }

    public function test_register_confirm_creates_user_and_returns_token(): void
    {
        Mail::fake();

        $this->postJson('/api/auth/register', [
            'email' => 'new@example.com',
            'phone' => '+70000000000',
            'password' => 'secret123',
        ])->assertStatus(202);

        $code = null;
        Mail::assertSent(CodeMail::class, function (CodeMail $mail) use (&$code) {
            $code = $mail->code;

            return $mail->purpose === 'register';
        });

        $this->postJson('/api/auth/register/confirm', [
            'email' => 'new@example.com',
            'code' => $code,
        ])->assertCreated()->assertJsonStructure(['token', 'user' => ['id', 'email']]);

        $this->assertDatabaseHas('users', ['email' => 'new@example.com']);
        $this->assertDatabaseMissing('pending_registrations', ['email' => 'new@example.com']);

        // Пароль из заявки должен позволять вход.
        $this->postJson('/api/auth/login', [
            'identifier' => 'new@example.com',
            'password' => 'secret123',
        ])->assertOk();
    }

    public function test_register_confirm_rejects_wrong_code(): void
    {
        Mail::fake();

        $this->postJson('/api/auth/register', [
            'email' => 'new@example.com',
            'phone' => '+70000000000',
            'password' => 'secret123',
        ])->assertStatus(202);

        $this->postJson('/api/auth/register/confirm', [
            'email' => 'new@example.com',
            'code' => '000000',
        ])->assertStatus(422);

        $this->assertDatabaseMissing('users', ['email' => 'new@example.com']);
    }

    public function test_login_with_email(): void
    {
        $user = User::factory()->create([
            'email' => 'a@example.com',
            'password' => 'secret123',
        ]);

        $this->postJson('/api/auth/login', [
            'identifier' => 'a@example.com',
            'password' => 'secret123',
        ])->assertOk()->assertJsonStructure(['token']);
    }

    public function test_login_fails_with_wrong_password(): void
    {
        User::factory()->create(['email' => 'a@example.com', 'password' => 'secret123']);

        $this->postJson('/api/auth/login', [
            'identifier' => 'a@example.com',
            'password' => 'wrong',
        ])->assertStatus(422);
    }

    public function test_me_requires_token(): void
    {
        $this->getJson('/api/user')->assertUnauthorized();
    }

    public function test_recovery_flow_resets_password(): void
    {
        $user = User::factory()->create(['email' => 'r@example.com', 'password' => 'oldpass123']);

        $this->postJson('/api/auth/recovery', ['email' => 'r@example.com'])->assertOk();

        $this->postJson('/api/auth/recovery/reset', [
            'email' => 'r@example.com',
            'password' => 'newpass123',
        ])->assertOk();

        $this->postJson('/api/auth/login', [
            'identifier' => 'r@example.com',
            'password' => 'newpass123',
        ])->assertOk();
    }

    public function test_telegram_init_data_validation(): void
    {
        $botToken = 'test-bot-token';
        $user = ['id' => 42, 'first_name' => 'Ivan', 'username' => 'ivan'];
        $params = [
            'auth_date' => (string) now()->timestamp,
            'query_id' => 'abc',
            'user' => json_encode($user, JSON_UNESCAPED_UNICODE),
        ];
        ksort($params);
        $pairs = [];
        foreach ($params as $k => $v) {
            $pairs[] = "$k=$v";
        }
        $secret = hash_hmac('sha256', $botToken, 'WebAppData', true);
        $hash = hash_hmac('sha256', implode("\n", $pairs), $secret);
        $params['hash'] = $hash;
        $initData = http_build_query($params);

        $service = new TelegramInitData($botToken);
        $this->assertNotNull($service->validate($initData));

        $tampered = new TelegramInitData('other-token');
        $this->assertNull($tampered->validate($initData));
    }
}
