<?php

namespace Tests\Feature;

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class ProfileAvatarTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
    }

    private function demo(): User
    {
        return User::where('email', 'demo@karta.app')->first();
    }

    public function test_upload_avatar_sets_url_and_stores_file(): void
    {
        Storage::fake('local');
        $user = $this->demo();
        Sanctum::actingAs($user);

        $resp = $this->postJson('/api/profile/avatar', [
            'avatar' => UploadedFile::fake()->image('me.jpg', 300, 300),
        ])->assertOk();

        $url = $resp->json('data.avatar_url');
        $this->assertNotEmpty($url);
        $this->assertStringContainsString("/users/{$user->id}/avatar", $url);

        $user->refresh();
        $this->assertNotNull($user->avatar_path);
        Storage::disk('local')->assertExists($user->avatar_path);

        // Профиль отдаёт тот же URL.
        $this->getJson('/api/profile')->assertOk()->assertJsonPath('user.avatar_url', $url);
    }

    public function test_avatar_is_served_publicly(): void
    {
        Storage::fake('local');
        $user = $this->demo();
        Sanctum::actingAs($user);

        $this->postJson('/api/profile/avatar', [
            'avatar' => UploadedFile::fake()->image('me.jpg', 120, 120),
        ])->assertOk();

        // Публичный роут отдачи (как <img>): доступен и обслуживает файл.
        $this->getJson("/api/users/{$user->id}/avatar")->assertOk();
    }

    public function test_upload_replaces_previous_file(): void
    {
        Storage::fake('local');
        $user = $this->demo();
        Sanctum::actingAs($user);

        $this->postJson('/api/profile/avatar', ['avatar' => UploadedFile::fake()->image('a.jpg')])->assertOk();
        $first = $user->refresh()->avatar_path;

        $this->postJson('/api/profile/avatar', ['avatar' => UploadedFile::fake()->image('b.jpg')])->assertOk();
        $second = $user->refresh()->avatar_path;

        $this->assertNotSame($first, $second);
        Storage::disk('local')->assertMissing($first);
        Storage::disk('local')->assertExists($second);
    }

    public function test_delete_avatar_clears_url_and_file(): void
    {
        Storage::fake('local');
        $user = $this->demo();
        Sanctum::actingAs($user);

        $this->postJson('/api/profile/avatar', ['avatar' => UploadedFile::fake()->image('a.jpg')])->assertOk();
        $path = $user->refresh()->avatar_path;

        $this->deleteJson('/api/profile/avatar')->assertOk()->assertJsonPath('data.avatar_url', null);

        $user->refresh();
        $this->assertNull($user->avatar_path);
        $this->assertNull($user->avatar_url);
        Storage::disk('local')->assertMissing($path);
    }

    public function test_rejects_non_image(): void
    {
        Storage::fake('local');
        Sanctum::actingAs($this->demo());

        $this->postJson('/api/profile/avatar', [
            'avatar' => UploadedFile::fake()->create('note.pdf', 100, 'application/pdf'),
        ])->assertStatus(422);
    }
}
