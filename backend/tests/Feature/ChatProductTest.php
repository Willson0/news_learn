<?php

namespace Tests\Feature;

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class ChatProductTest extends TestCase
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

    public function test_send_text_with_reply(): void
    {
        $user = $this->demo();
        Sanctum::actingAs($user);

        $first = $this->postJson('/api/chats/oil/messages', ['body' => 'Первое'])
            ->assertCreated()->json('data');

        $this->postJson('/api/chats/oil/messages', [
            'body' => 'Ответ',
            'reply_to' => $first['id'],
        ])->assertCreated()
            ->assertJsonPath('data.reply.id', $first['id'])
            ->assertJsonPath('data.reply.text', 'Первое');
    }

    public function test_send_image_attachment_and_listed_in_media(): void
    {
        Storage::fake('local');
        Sanctum::actingAs($this->demo());

        $resp = $this->postJson('/api/chats/oil/messages', [
            'kind' => 'image',
            'attachment' => UploadedFile::fake()->image('photo.jpg', 320, 200),
        ])->assertCreated();

        $resp->assertJsonPath('data.image.width', 320);
        $this->assertNotEmpty($resp->json('data.image.url'));

        $this->getJson('/api/chats/oil/attachments/image')->assertOk()
            ->assertJsonCount(1, 'data');

        // Файл отдаётся по подписанной ссылке.
        $url = $resp->json('data.image.url');
        $this->get($url)->assertOk();
    }

    public function test_send_file_and_voice(): void
    {
        Storage::fake('local');
        Sanctum::actingAs($this->demo());

        $this->postJson('/api/chats/oil/messages', [
            'kind' => 'file',
            'attachment' => UploadedFile::fake()->create('doc.zip', 1900, 'application/zip'),
        ])->assertCreated()
            ->assertJsonPath('data.file.ext', 'zip');

        $this->postJson('/api/chats/oil/messages', [
            'kind' => 'voice',
            'duration' => 42,
            'attachment' => UploadedFile::fake()->create('voice.m4a', 100, 'audio/mp4'),
        ])->assertCreated()
            ->assertJsonPath('data.voice.duration', '0:42');

        $this->getJson('/api/chats/oil/attachments/file')->assertOk()->assertJsonCount(1, 'data');
        $this->getJson('/api/chats/oil/attachments/voice')->assertOk()->assertJsonCount(1, 'data');
    }

    public function test_empty_message_rejected(): void
    {
        Sanctum::actingAs($this->demo());

        $this->postJson('/api/chats/oil/messages', ['body' => '   '])
            ->assertStatus(422);
    }

    public function test_reactions_toggle(): void
    {
        Sanctum::actingAs($this->demo());

        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'hi'])->json('data.id');

        $this->postJson("/api/chats/oil/messages/{$id}/react", ['emoji' => '👍'])
            ->assertOk()
            ->assertJsonPath('data.reactions.0.emoji', '👍')
            ->assertJsonPath('data.reactions.0.count', 1)
            ->assertJsonPath('data.reactions.0.mine', true);

        // Повторный клик снимает реакцию.
        $this->postJson("/api/chats/oil/messages/{$id}/react", ['emoji' => '👍'])
            ->assertOk()
            ->assertJsonCount(0, 'data.reactions');
    }

    public function test_pin_toggle_reflected_in_messages(): void
    {
        Sanctum::actingAs($this->demo());

        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'pin me'])->json('data.id');

        $this->postJson("/api/chats/oil/messages/{$id}/pin")->assertOk()
            ->assertJsonPath('pinned.id', $id);

        $this->getJson('/api/chats/oil/messages')->assertOk()
            ->assertJsonPath('pinned.text', 'pin me');

        $this->postJson("/api/chats/oil/messages/{$id}/pin")->assertOk()
            ->assertJsonPath('pinned', null);
    }

    public function test_author_can_delete_own_message(): void
    {
        Sanctum::actingAs($this->demo());

        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'bye'])->json('data.id');

        $this->deleteJson("/api/chats/oil/messages/{$id}")->assertOk();
        $this->assertDatabaseMissing('messages', ['id' => $id]);
    }

    public function test_non_author_cannot_delete(): void
    {
        $user = $this->demo();
        Sanctum::actingAs($user);
        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'mine'])->json('data.id');

        $other = User::factory()->create();
        Sanctum::actingAs($other);
        $this->deleteJson("/api/chats/oil/messages/{$id}")->assertForbidden();
    }

    public function test_participants_list_posters(): void
    {
        Sanctum::actingAs($this->demo());
        $this->postJson('/api/chats/oil/messages', ['body' => 'hi'])->assertCreated();

        $this->getJson('/api/chats/oil/participants')->assertOk()
            ->assertJsonFragment(['name' => 'Артем']);
    }

    public function test_links_tab_extracts_urls(): void
    {
        Sanctum::actingAs($this->demo());
        $this->postJson('/api/chats/oil/messages', ['body' => 'смотри https://rbk.ru/news вот'])->assertCreated();

        $this->getJson('/api/chats/oil/attachments/link')->assertOk()
            ->assertJsonFragment(['url' => 'https://rbk.ru/news']);
    }

    public function test_user_profile_endpoint(): void
    {
        $user = $this->demo();
        Sanctum::actingAs($user);

        $this->getJson("/api/users/{$user->id}")->assertOk()
            ->assertJsonPath('data.name', 'Артем')
            ->assertJsonPath('data.username', '@AlexeyRub');
    }

    public function test_read_receipt_marks_mine_read_when_other_reads(): void
    {
        $author = $this->demo();
        Sanctum::actingAs($author);
        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'read me'])->json('data.id');

        // Другой участник (админ) открывает чат — помечает прочитанным.
        $admin = User::factory()->create(['telegram_id' => config('admin.root_telegram_id')]);
        Sanctum::actingAs($admin);
        $this->getJson('/api/chats/oil/messages')->assertOk();

        // Автор снова видит своё сообщение прочитанным.
        Sanctum::actingAs($author);
        $resp = $this->getJson('/api/chats/oil/messages')->assertOk();
        $mine = collect($resp->json('data'))->firstWhere('id', $id);
        $this->assertTrue($mine['read']);
    }

    public function test_direct_chat_access_isolation(): void
    {
        $other = User::factory()->create();
        $directSlug = 'dm-'.$this->demo()->id;

        Sanctum::actingAs($other);
        // Чужой личный чат недоступен не-админу.
        $this->getJson("/api/chats/{$directSlug}/messages")->assertNotFound();
    }
}
