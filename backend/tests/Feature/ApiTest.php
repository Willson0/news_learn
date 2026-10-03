<?php

namespace Tests\Feature;

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class ApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
    }

    public function test_ping(): void
    {
        $this->getJson('/api/ping')->assertOk()->assertJson(['status' => 'ok']);
    }

    public function test_instruments_are_public(): void
    {
        $this->getJson('/api/instruments')
            ->assertOk()
            ->assertJsonFragment(['key' => 'gold']);
    }

    public function test_reports_list_and_filter(): void
    {
        Sanctum::actingAs(User::first());

        $this->getJson('/api/reports')->assertOk()
            ->assertJsonFragment(['title' => 'Нефть в 2026 году, как она?']);

        $resp = $this->getJson('/api/reports?instrument=btc')->assertOk();
        $this->assertCount(1, $resp->json('data'));

        $this->getJson('/api/reports?search=золот')->assertOk()
            ->assertJsonFragment(['material' => 'gold']);
    }

    public function test_profile_payload(): void
    {
        Sanctum::actingAs(User::where('email', 'demo@karta.app')->first());

        $this->getJson('/api/profile')->assertOk()
            ->assertJsonPath('subscription.active', true)
            ->assertJsonFragment(['key' => 'gold', 'label' => 'Золото', 'on' => true]);
    }

    public function test_sync_instruments(): void
    {
        $user = User::where('email', 'demo@karta.app')->first();
        Sanctum::actingAs($user);

        $this->putJson('/api/profile/instruments', ['instruments' => ['btc', 'gold']])
            ->assertOk();

        $this->assertEqualsCanonicalizing(['btc', 'gold'], $user->fresh()->instruments->pluck('key')->all());
    }

    public function test_chats_and_messages(): void
    {
        $user = User::first();
        Sanctum::actingAs($user);

        $this->getJson('/api/chats')->assertOk()
            ->assertJsonFragment(['title' => 'Общий чат']);

        $this->getJson('/api/chats/oil/messages')->assertOk()
            ->assertJsonPath('chat.title', 'Нефть 12.12.27');

        $this->postJson('/api/chats/oil/messages', ['body' => 'Привет!'])
            ->assertCreated()
            ->assertJsonPath('data.mine', true)
            ->assertJsonPath('data.text', 'Привет!');
    }

    public function test_change_password(): void
    {
        $user = User::where('email', 'demo@karta.app')->first();
        Sanctum::actingAs($user);

        $this->putJson('/api/account/password', [
            'password' => 'brandnew123',
            'password_confirmation' => 'brandnew123',
        ])->assertOk();
    }
}
