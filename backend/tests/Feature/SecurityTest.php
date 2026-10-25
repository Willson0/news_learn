<?php

namespace Tests\Feature;

use App\Models\Chat;
use App\Models\Report;
use App\Models\Subscription;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Hash;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

/**
 * Проверки защиты: платный доступ на сервере, лимиты, вложения, токены.
 */
class SecurityTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
    }

    private function freeUser(): User
    {
        return User::factory()->create(['email' => 'free@example.com', 'password' => 'password-123']);
    }

    public function test_reports_are_hidden_without_subscription(): void
    {
        $report = Report::where('status', '!=', 'draft')->firstOrFail();
        Sanctum::actingAs($this->freeUser());

        $this->getJson('/api/reports')->assertOk()->assertExactJson(['data' => []]);
        $this->getJson("/api/reports/{$report->id}")->assertForbidden();
    }

    public function test_subscriber_gets_signed_file_links(): void
    {
        $demo = User::where('email', 'demo@karta.app')->firstOrFail();
        Report::where('status', '!=', 'draft')->firstOrFail()->update(['cover_path' => 'reports/covers/x.png']);
        Sanctum::actingAs($demo);

        $item = collect($this->getJson('/api/reports')->assertOk()->json('data'))->firstWhere('cover_url', '!=', null);
        $this->assertNotNull($item);
        $this->assertStringContainsString('signature=', $item['cover_url']);
    }

    public function test_draft_report_is_not_shown_to_users(): void
    {
        $report = Report::where('status', '!=', 'draft')->firstOrFail();
        $report->update(['status' => 'draft']);
        Sanctum::actingAs(User::where('email', 'demo@karta.app')->firstOrFail());

        $this->getJson("/api/reports/{$report->id}")->assertNotFound();
    }

    public function test_report_chats_need_subscription(): void
    {
        $chat = Chat::where('section', 'reports')->first();
        if (! $chat) {
            $this->markTestSkipped('В демо-данных нет чатов отчётов');
        }
        Sanctum::actingAs($this->freeUser());

        $this->getJson("/api/chats/{$chat->slug}/messages")->assertForbidden();
        $sections = collect($this->getJson('/api/chats')->assertOk()->json('data'))->pluck('key');
        $this->assertNotContains('reports', $sections);
    }

    public function test_expired_gift_does_not_come_back_by_enabling_auto_pay(): void
    {
        $user = $this->freeUser();
        Subscription::create(['user_id' => $user->id, 'active' => true, 'plan' => 'gift', 'until' => today()->subDays(10), 'auto_pay' => false]);
        Sanctum::actingAs($user);

        $this->putJson('/api/subscription', ['auto_pay' => true])->assertOk();

        $this->getJson('/api/subscription')->assertOk()->assertJsonPath('data.active', false);
        $this->getJson('/api/reports')->assertExactJson(['data' => []]);
    }

    public function test_user_cannot_create_subscription_record_himself(): void
    {
        $user = $this->freeUser();
        Sanctum::actingAs($user);

        $this->putJson('/api/subscription', ['auto_pay' => true])->assertOk();

        $this->assertDatabaseMissing('subscriptions', ['user_id' => $user->id]);
    }

    public function test_login_is_rate_limited(): void
    {
        $this->freeUser();
        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/auth/login', ['identifier' => 'free@example.com', 'password' => 'wrong'])->assertStatus(422);
        }

        $this->postJson('/api/auth/login', ['identifier' => 'free@example.com', 'password' => 'password-123'])->assertStatus(429);
    }

    public function test_recovery_codes_are_rate_limited(): void
    {
        for ($i = 0; $i < 3; $i++) {
            $this->postJson('/api/auth/recovery', ['email' => 'free@example.com'])->assertOk();
        }

        $this->postJson('/api/auth/recovery', ['email' => 'free@example.com'])->assertStatus(429);
    }

    public function test_html_file_cannot_be_sent_as_photo_and_files_are_downloaded_not_rendered(): void
    {
        Sanctum::actingAs(User::where('email', 'demo@karta.app')->firstOrFail());
        $chat = Chat::where('section', 'main')->firstOrFail();

        // Настоящий файл (а не fake): тип определяется по содержимому, как в бою.
        $tmp = tempnam(sys_get_temp_dir(), 'att');
        file_put_contents($tmp, '<html><script>alert(1)</script></html>');
        $this->post("/api/chats/{$chat->slug}/messages", [
            'kind' => 'image',
            'attachment' => new UploadedFile($tmp, 'x.png', 'image/png', null, true),
        ], ['Accept' => 'application/json'])->assertStatus(422);

        $resp = $this->post("/api/chats/{$chat->slug}/messages", [
            'kind' => 'file',
            'attachment' => UploadedFile::fake()->createWithContent('page.html', '<html><script>alert(1)</script></html>'),
        ], ['Accept' => 'application/json'])->assertCreated();

        $url = $resp->json('data.file.url');
        $this->assertNotNull($url);
        $this->get($url)->assertOk()
            ->assertHeader('Content-Type', 'application/octet-stream')
            ->assertHeader('X-Content-Type-Options', 'nosniff');
    }

    public function test_password_change_signs_out_other_devices(): void
    {
        $user = $this->freeUser();
        $other = $user->createToken('other')->plainTextToken;
        $current = $user->createToken('current')->plainTextToken;

        $this->withToken($current)->putJson('/api/account/password', [
            'password' => 'new-password-1', 'password_confirmation' => 'new-password-1',
        ])->assertOk();

        $this->assertSame(1, $user->tokens()->count());
        $this->assertTrue(Hash::check('new-password-1', $user->fresh()->password));
        $this->app['auth']->forgetGuards();
        $this->withToken($other)->getJson('/api/user')->assertUnauthorized();
    }

    public function test_admin_pages_have_strict_security_headers(): void
    {
        $this->getJson('/admin/api/me')
            ->assertHeader('X-Frame-Options', 'DENY')
            ->assertHeader('X-Content-Type-Options', 'nosniff');
        $this->assertStringContainsString("frame-ancestors 'none'", (string) $this->getJson('/admin/api/me')->headers->get('Content-Security-Policy'));
    }
}
