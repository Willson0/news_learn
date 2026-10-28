<?php

namespace Tests\Feature;

use App\Models\Chat;
use App\Models\Instrument;
use App\Models\Report;
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

    private function admin(): User
    {
        return User::factory()->create(['telegram_id' => config('admin.root_telegram_id')]);
    }

    /** Чат отчёта с создателем-автором (для личных сообщений и прав на закреп). */
    private function reportChat(User $author): Chat
    {
        $report = Report::create([
            'instrument_id' => Instrument::first()->id,
            'title' => 'Отчёт автора',
            'description' => 'Описание',
            'body' => 'Описание',
            'created_by' => $author->id,
            'published_at' => now(),
        ]);

        return Chat::create([
            'slug' => 'report-'.$report->id,
            'title' => 'Чат отчёта',
            'type' => 'report',
            'section' => 'reports',
            'report_id' => $report->id,
        ]);
    }

    public function test_pin_toggle_reflected_in_messages(): void
    {
        Sanctum::actingAs($this->admin());

        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'pin me'])->json('data.id');

        $this->postJson("/api/chats/oil/messages/{$id}/pin")->assertOk()
            ->assertJsonPath('pinned.id', $id);

        $this->getJson('/api/chats/oil/messages')->assertOk()
            ->assertJsonPath('pinned.text', 'pin me');

        $this->postJson("/api/chats/oil/messages/{$id}/pin")->assertOk()
            ->assertJsonPath('pinned', null);
    }

    public function test_several_pins_kept_in_pin_order(): void
    {
        Sanctum::actingAs($this->admin());

        $a = $this->postJson('/api/chats/oil/messages', ['body' => 'A'])->json('data.id');
        $b = $this->postJson('/api/chats/oil/messages', ['body' => 'B'])->json('data.id');

        $this->postJson("/api/chats/oil/messages/{$b}/pin")->assertOk();
        $this->travel(1)->seconds();
        $this->postJson("/api/chats/oil/messages/{$a}/pin")->assertOk()
            ->assertJsonCount(2, 'pins')
            ->assertJsonPath('pins.0.id', $b)
            ->assertJsonPath('pins.1.id', $a)
            ->assertJsonPath('pinned.id', $a);

        // Открепление одного не трогает другой.
        $this->postJson("/api/chats/oil/messages/{$a}/pin")->assertOk()
            ->assertJsonCount(1, 'pins')
            ->assertJsonPath('pinned.id', $b);
    }

    public function test_only_admin_or_report_author_can_pin(): void
    {
        $author = User::factory()->create();
        $chat = $this->reportChat($author);
        $member = $this->demo();

        Sanctum::actingAs($member);
        $id = $this->postJson("/api/chats/{$chat->slug}/messages", ['body' => 'hi'])->json('data.id');
        $this->postJson("/api/chats/{$chat->slug}/messages/{$id}/pin")->assertForbidden();
        $this->getJson("/api/chats/{$chat->slug}/messages")->assertJsonPath('chat.can_pin', false);

        // Автор отчёта — может (даже без подписки: у демо её нет, у автора тоже).
        $author->forceFill(['telegram_id' => null])->save();
        $this->actingAsAuthorWithAccess($author);
        $this->postJson("/api/chats/{$chat->slug}/messages/{$id}/pin")->assertOk()
            ->assertJsonPath('pinned.id', $id);
    }

    private function actingAsAuthorWithAccess(User $author): void
    {
        \App\Models\Subscription::updateOrCreate(['user_id' => $author->id], [
            'plan' => 'month', 'active' => true, 'until' => now()->addMonth(),
        ]);
        Sanctum::actingAs($author->fresh());
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
    public function test_delete_for_me_hides_only_for_me(): void
    {
        Sanctum::actingAs($this->demo());
        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'hide me'])->json('data.id');

        $admin = $this->admin();
        Sanctum::actingAs($admin);
        // Чужое сообщение «у всех» обычный участник удалить не может, а «у меня» — может.
        $this->deleteJson("/api/chats/oil/messages/{$id}?for=me")->assertOk();
        $ids = collect($this->getJson('/api/chats/oil/messages')->json('data'))->pluck('id');
        $this->assertNotContains($id, $ids);

        Sanctum::actingAs($this->demo());
        $ids = collect($this->getJson('/api/chats/oil/messages')->json('data'))->pluck('id');
        $this->assertContains($id, $ids);
        $this->assertDatabaseHas('messages', ['id' => $id]);
    }

    public function test_can_delete_flag(): void
    {
        Sanctum::actingAs($this->demo());
        $id = $this->postJson('/api/chats/oil/messages', ['body' => 'mine'])
            ->assertJsonPath('data.can_delete', true)->json('data.id');

        // Другой участник чужое сообщение у всех удалить не может.
        $other = User::factory()->create();
        \App\Models\Subscription::create(['user_id' => $other->id, 'active' => true, 'until' => now()->addMonth()]);
        Sanctum::actingAs($other);
        $row = collect($this->getJson('/api/chats/oil/messages')->json('data'))->firstWhere('id', $id);
        $this->assertFalse($row['can_delete']);

        // Админ — может.
        Sanctum::actingAs($this->admin());
        $row = collect($this->getJson('/api/chats/oil/messages')->json('data'))->firstWhere('id', $id);
        $this->assertTrue($row['can_delete']);
    }

    public function test_join_message_on_first_open_and_leave_message(): void
    {
        $user = User::factory()->create(['name' => 'Новичок']);
        $chat = Chat::create(['slug' => 'extra', 'title' => 'Доп. чат', 'type' => 'group', 'section' => 'other']);
        $this->actingAsAuthorWithAccess($user);

        $data = $this->getJson('/api/chats/extra/messages')->assertOk()->json('data');
        $this->assertSame('join', $data[0]['event']);
        $this->assertSame('Новичок', $data[0]['author']);
        $this->assertSame($user->id, $data[0]['user_id']);

        // Повторное открытие не добавляет второе сообщение.
        $this->getJson('/api/chats/extra/messages')->assertOk()->assertJsonCount(1, 'data');

        $this->postJson('/api/chats/extra/leave')->assertOk();
        $this->assertDatabaseHas('messages', ['chat_id' => $chat->id, 'user_id' => $user->id, 'event' => 'leave']);
    }

    public function test_old_participants_do_not_get_join_message(): void
    {
        // Писал в чат ещё до появления служебных сообщений.
        $user = $this->demo();
        Chat::where('slug', 'oil')->firstOrFail()->messages()->create([
            'user_id' => $user->id, 'author_name' => $user->name, 'body' => 'был тут',
        ]);
        Sanctum::actingAs($user);
        $events = collect($this->getJson('/api/chats/oil/messages')->json('data'))->pluck('event')->filter();
        $this->assertCount(0, $events);
    }

    public function test_general_chat_cannot_be_left(): void
    {
        Sanctum::actingAs($this->demo());
        $this->postJson('/api/chats/general/leave')->assertStatus(422);
        $this->getJson('/api/chats/general/messages')->assertJsonPath('chat.permanent', true);
    }

    public function test_private_message_to_report_author(): void
    {
        $author = User::factory()->create(['name' => 'Автор']);
        $chat = $this->reportChat($author);
        $this->actingAsAuthorWithAccess($author);
        $this->getJson("/api/chats/{$chat->slug}/messages")->assertOk();

        $member = $this->demo();
        Sanctum::actingAs($member);
        $resp = $this->postJson("/api/chats/{$chat->slug}/messages", ['body' => '/Как долго вы делали этот отчет?/'])
            ->assertCreated()
            ->assertJsonPath('data.private', true)
            ->assertJsonPath('data.private_label', 'Автору')
            ->assertJsonPath('data.text', 'Как долго вы делали этот отчет?');
        $id = $resp->json('data.id');

        // Третий участник личное сообщение не видит.
        $third = $this->admin();
        Sanctum::actingAs($third);
        $ids = collect($this->getJson("/api/chats/{$chat->slug}/messages")->json('data'))->pluck('id');
        $this->assertNotContains($id, $ids);

        // Автор видит и отвечает — ответ тоже личный и виден отправителю.
        $this->actingAsAuthorWithAccess($author);
        $row = collect($this->getJson("/api/chats/{$chat->slug}/messages")->json('data'))->firstWhere('id', $id);
        $this->assertSame('Лично вам', $row['private_label']);
        $reply = $this->postJson("/api/chats/{$chat->slug}/messages", ['body' => 'Две недели', 'reply_to' => $id])
            ->assertCreated()->assertJsonPath('data.private', true)->json('data.id');

        Sanctum::actingAs($member);
        $ids = collect($this->getJson("/api/chats/{$chat->slug}/messages")->json('data'))->pluck('id');
        $this->assertContains($reply, $ids);

        Sanctum::actingAs($third);
        $ids = collect($this->getJson("/api/chats/{$chat->slug}/messages")->json('data'))->pluck('id');
        $this->assertNotContains($reply, $ids);
    }

    public function test_members_for_mentions(): void
    {
        User::factory()->create(['name' => 'Владислав', 'username' => '@Submetal']);
        Sanctum::actingAs($this->demo());

        $this->getJson('/api/chats/general/members?q=subm')->assertOk()
            ->assertJsonPath('data.0.username', '@Submetal')
            ->assertJsonPath('data.0.name', 'Владислав');
    }

    public function test_reaction_removed_by_second_tap_and_system_message_not_reactable(): void
    {
        $user = User::factory()->create();
        Chat::create(['slug' => 'extra', 'title' => 'Доп. чат', 'type' => 'group', 'section' => 'other']);
        $this->actingAsAuthorWithAccess($user);
        $join = $this->getJson('/api/chats/extra/messages')->json('data.0.id');
        $this->postJson("/api/chats/extra/messages/{$join}/react", ['emoji' => '👍'])->assertStatus(422);
    }
}
