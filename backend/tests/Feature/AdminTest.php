<?php

namespace Tests\Feature;

use App\Models\Admin;
use App\Models\Chat;
use App\Models\Report;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class AdminTest extends TestCase
{
    use RefreshDatabase;

    private const ROOT = 1337592809;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
        Storage::fake('local');
    }

    private function root(): User
    {
        return User::factory()->create(['telegram_id' => self::ROOT]);
    }

    public function test_root_admin_is_admin_by_telegram_id(): void
    {
        Sanctum::actingAs($this->root());

        $this->getJson('/api/user')->assertOk()
            ->assertJsonPath('user.is_admin', true)
            ->assertJsonPath('user.is_root_admin', true);
    }

    public function test_regular_user_cannot_use_admin_api(): void
    {
        Sanctum::actingAs(User::factory()->create(['telegram_id' => 42]));

        $this->getJson('/api/user')->assertJsonPath('user.is_admin', false);
        $this->getJson('/api/admin/admins')->assertForbidden();
        $this->postJson('/api/admin/admins', ['telegram_id' => '42'])->assertForbidden();
        $this->postJson('/api/admin/reports', [])->assertForbidden();
    }

    public function test_admin_can_add_admin_who_then_gets_access(): void
    {
        Sanctum::actingAs($this->root());

        $this->postJson('/api/admin/admins', ['telegram_id' => '555'])->assertCreated();
        $this->postJson('/api/admin/admins', ['telegram_id' => '555'])->assertStatus(422);

        $this->getJson('/api/admin/admins')->assertOk()
            ->assertJsonPath('data.0.telegram_id', (string) self::ROOT)
            ->assertJsonPath('data.0.root', true)
            ->assertJsonPath('data.1.telegram_id', '555');

        $newAdmin = User::factory()->create(['telegram_id' => 555]);
        Sanctum::actingAs($newAdmin);
        $this->getJson('/api/admin/admins')->assertOk();
        // Новый админ тоже может добавлять админов.
        $this->postJson('/api/admin/admins', ['telegram_id' => '777'])->assertCreated();
    }

    public function test_root_admin_cannot_be_removed_or_duplicated(): void
    {
        Admin::create(['telegram_id' => 555]);
        Sanctum::actingAs(User::factory()->create(['telegram_id' => 555]));

        $this->deleteJson('/api/admin/admins/'.self::ROOT)->assertForbidden();
        $this->postJson('/api/admin/admins', ['telegram_id' => (string) self::ROOT])->assertStatus(422);

        $this->deleteJson('/api/admin/admins/555')->assertOk();
        $this->assertFalse(User::where('telegram_id', 555)->first()->isAdmin());
    }

    public function test_create_report_validation_messages(): void
    {
        Sanctum::actingAs($this->root());

        $this->postJson('/api/admin/reports', [])->assertStatus(422)
            ->assertJsonPath('errors.title.0', 'Напишите название')
            ->assertJsonPath('errors.description.0', 'Напишите описание')
            ->assertJsonPath('errors.instrument.0', 'Выберите инструмент')
            ->assertJsonPath('errors.cover.0', 'Загрузите обложку');
    }

    public function test_create_update_and_delete_report_with_chat(): void
    {
        Sanctum::actingAs($this->root());

        $resp = $this->post('/api/admin/reports', [
            'title' => 'Нефть 2027',
            'description' => 'Описание',
            'chart_url' => 'http://chart001.ru',
            'instrument' => 'wti',
            'cover' => UploadedFile::fake()->image('jpg_0012.jpg'),
            'html' => UploadedFile::fake()->createWithContent('Нефть 2026.html', '<h1>Отчёт</h1>'),
        ], ['Accept' => 'application/json'])->assertCreated();

        $id = $resp->json('data.id');
        $report = Report::find($id);
        $this->assertSame('jpg_0012.jpg', $report->cover_name);
        $this->assertNotNull($resp->json('data.cover_url'));
        $this->assertSame('report-'.$id, $resp->json('data.chat'));
        $this->assertDatabaseHas('chats', ['report_id' => $id, 'section' => 'reports']);

        $this->get("/api/reports/{$id}/html")->assertOk()
            ->assertHeader('Content-Security-Policy', 'sandbox allow-scripts');
        $this->get("/api/reports/{$id}/cover")->assertOk();

        $this->post("/api/admin/reports/{$id}", [
            'title' => 'Нефть 2027, обновлено',
            'description' => 'Новое описание',
            'instrument' => 'brent',
            'remove_html' => '1',
        ], ['Accept' => 'application/json'])->assertOk()
            ->assertJsonPath('data.material', 'brent')
            ->assertJsonPath('data.html_url', null);

        $this->deleteJson("/api/admin/reports/{$id}")->assertOk();
        $this->assertDatabaseMissing('reports', ['id' => $id]);
        $this->assertDatabaseMissing('chats', ['report_id' => $id]);
    }

    public function test_direct_chats_are_private_and_visible_to_admins(): void
    {
        $user = User::where('email', 'demo@karta.app')->first();
        $other = User::factory()->create();

        // Пользователь видит свой чат как «admin».
        Sanctum::actingAs($user);
        $this->getJson('/api/chats')->assertOk()->assertJsonPath('data.0.items.0.id', 'admin');
        $this->postJson('/api/chats/admin/messages', ['body' => 'Помогите'])->assertCreated();

        // Чужой личный чат недоступен.
        Sanctum::actingAs($other);
        $this->getJson('/api/chats/dm-'.$user->id.'/messages')->assertNotFound();

        // Админ видит личные чаты всех и может ответить.
        Sanctum::actingAs($this->root());
        $sections = collect($this->getJson('/api/chats')->assertOk()->json('data'));
        $this->assertNull($sections->firstWhere('key', 'admin'));
        $direct = $sections->firstWhere('key', 'direct');
        $this->assertSame('dm-'.$user->id, $direct['items'][0]['id']);
        $this->assertSame('Помогите', $direct['items'][0]['preview']);

        $this->postJson('/api/chats/dm-'.$user->id.'/messages', ['body' => 'Отвечаю'])
            ->assertCreated()->assertJsonPath('data.role', 'Админ');
    }

    public function test_admin_can_rename_chat(): void
    {
        Sanctum::actingAs($this->root());

        $this->post('/api/admin/chats/oil', [
            'title' => 'Нефть 12.12.28',
            'avatar' => UploadedFile::fake()->image('a.png'),
        ], ['Accept' => 'application/json'])->assertOk()
            ->assertJsonPath('data.title', 'Нефть 12.12.28');

        $this->assertNotNull(Chat::where('slug', 'oil')->first()->avatar_path);
        $this->get('/api/chats/oil/avatar')->assertOk();
    }
}
