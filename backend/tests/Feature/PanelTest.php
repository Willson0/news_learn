<?php

namespace Tests\Feature;

use App\Models\AppSetting;
use App\Models\Chat;
use App\Models\DeviceToken;
use App\Models\Invoice;
use App\Models\PanelAdmin;
use App\Models\Payment;
use App\Models\Report;
use App\Models\Subscription;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class PanelTest extends TestCase
{
    use RefreshDatabase;

    private PanelAdmin $panel;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
        config([
            'services.tbank.terminal_key' => 'test-terminal',
            'services.tbank.password' => 'test-pass',
            'services.tbank.api_base' => 'https://api.tbank.test/v2',
        ]);
        $this->panel = PanelAdmin::create(['login' => 'boss', 'password' => 'secret-pass']);
    }

    private function asPanel(): static
    {
        return $this->actingAs($this->panel, 'panel');
    }

    /** Ключ сервисного аккаунта FCM для тестов: токен получаем через подписанный JWT. */
    private function fakeFcm(): void
    {
        $key = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
        openssl_pkey_export($key, $pem);
        $path = storage_path('framework/testing/fcm-'.uniqid().'.json');
        @mkdir(dirname($path), 0777, true);
        file_put_contents($path, json_encode(['client_email' => 'svc@test.iam', 'private_key' => $pem]));

        config(['services.fcm.project_id' => 'karta-test', 'services.fcm.credentials' => $path]);
        Http::fake([
            'oauth2.googleapis.com/token' => Http::response(['access_token' => 'tok']),
            'fcm.googleapis.com/*' => Http::response(['name' => 'message']),
        ]);
    }

    private function user(array $attrs = []): User
    {
        return User::factory()->create($attrs);
    }

    public function test_panel_api_requires_panel_login(): void
    {
        $this->getJson('/admin/api/users')->assertUnauthorized();
        $this->getJson('/admin/api/me')->assertUnauthorized();
    }

    public function test_login_with_wrong_password_is_rejected(): void
    {
        $this->postJson('/admin/api/login', ['login' => 'boss', 'password' => 'nope'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('login');
    }

    public function test_login_then_me_and_logout(): void
    {
        $this->postJson('/admin/api/login', ['login' => 'boss', 'password' => 'secret-pass'])
            ->assertOk()
            ->assertJson(['login' => 'boss']);

        $this->getJson('/admin/api/me')->assertOk()->assertJson(['login' => 'boss']);
        $this->postJson('/admin/api/logout')->assertOk();
    }

    public function test_spa_shell_is_served_for_admin_routes(): void
    {
        $this->get('/admin/users')->assertOk()->assertSee('id="admin"', false);
    }

    public function test_account_change_needs_current_password(): void
    {
        $this->asPanel()->putJson('/admin/api/account', [
            'login' => 'new-boss',
            'current_password' => 'wrong',
        ])->assertStatus(422)->assertJsonValidationErrors('current_password');

        $this->asPanel()->putJson('/admin/api/account', [
            'login' => 'new-boss',
            'password' => 'brand-new-pass',
            'password_confirmation' => 'brand-new-pass',
            'current_password' => 'secret-pass',
        ])->assertOk()->assertJson(['login' => 'new-boss']);

        $this->assertTrue(Hash::check('brand-new-pass', $this->panel->fresh()->password));
    }

    public function test_users_list_filters_by_group_and_search(): void
    {
        $active = $this->user(['name' => 'Активный Пётр']);
        Subscription::create(['user_id' => $active->id, 'active' => true, 'until' => now()->addMonth(), 'amount' => 1490, 'months' => 1]);
        $expired = $this->user(['name' => 'Истёкшая Анна']);
        Subscription::create(['user_id' => $expired->id, 'active' => false, 'until' => now()->subDay()]);
        $this->user(['name' => 'Новый Иван']);

        // Демо-пользователь из сидов тоже активен, поэтому проверяем по именам.
        $this->asPanel()->getJson('/admin/api/users?filter=active')
            ->assertOk()
            ->assertJsonFragment(['name' => 'Активный Пётр'])
            ->assertJsonMissing(['name' => 'Новый Иван']);

        $this->asPanel()->getJson('/admin/api/users?filter=expired')
            ->assertOk()->assertJsonCount(1, 'data')->assertJsonPath('data.0.name', 'Истёкшая Анна');

        $this->asPanel()->getJson('/admin/api/users?filter=free')
            ->assertOk()
            ->assertJsonFragment(['name' => 'Новый Иван'])
            ->assertJsonMissing(['name' => 'Активный Пётр'])
            ->assertJsonMissing(['name' => 'Истёкшая Анна']);

        $this->asPanel()->getJson('/admin/api/users?q=Пётр')
            ->assertOk()->assertJsonCount(1, 'data');
    }

    public function test_invoice_uses_default_price_and_pushes_to_user(): void
    {
        $this->fakeFcm();
        AppSetting::put('default_price', 1700);
        $user = $this->user();
        DeviceToken::create(['user_id' => $user->id, 'token' => 'dev-1', 'platform' => 'android']);

        Http::fake([
            'api.tbank.test/v2/Init' => Http::response([
                'Success' => true, 'Status' => 'NEW', 'PaymentId' => 'pay-inv-1', 'PaymentURL' => 'https://tbank.test/inv-1',
            ]),
            'oauth2.googleapis.com/token' => Http::response(['access_token' => 'tok']),
            'fcm.googleapis.com/*' => Http::response(['name' => 'message']),
        ]);

        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['months' => 3])
            ->assertCreated()
            ->assertJsonPath('data.amount', 1700)
            ->assertJsonPath('data.confirmation_url', 'https://tbank.test/inv-1');

        Http::assertSent(fn ($r) => str_ends_with($r->url(), '/Init')
            && $r['Amount'] === 170000
            && $r['Recurrent'] === 'Y'
            && $r['CustomerKey'] === 'user-'.$user->id
            && str_starts_with($r['OrderId'], 'inv-'));

        Http::assertSent(fn ($r) => str_contains($r->url(), 'fcm.googleapis.com')
            && $r['message']['token'] === 'dev-1'
            && $r['message']['data']['url'] === 'https://tbank.test/inv-1');

        $this->assertDatabaseHas('invoices', ['user_id' => $user->id, 'amount' => 1700, 'months' => 3, 'status' => 'pending', 'created_by' => 'boss', 'tbank_payment_id' => 'pay-inv-1']);
    }

    public function test_invoice_is_refused_for_user_with_active_subscription(): void
    {
        $user = $this->user();
        Subscription::create(['user_id' => $user->id, 'active' => true, 'until' => now()->addMonth()]);

        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['months' => 1])
            ->assertStatus(422);
    }

    public function test_paid_invoice_activates_subscription_and_records_payment(): void
    {
        $user = $this->user();
        Http::fake([
            'api.tbank.test/v2/Init' => Http::response([
                'Success' => true, 'Status' => 'NEW', 'PaymentId' => 'pay-inv-2', 'PaymentURL' => 'https://tbank.test/inv-2',
            ]),
        ]);
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['amount' => 2500, 'months' => 6])->assertCreated();
        $invoice = Invoice::firstOrFail();

        Http::fake([
            'api.tbank.test/v2/GetState' => Http::response([
                'Success' => true,
                'Status' => 'CONFIRMED',
                'PaymentId' => 'pay-inv-2',
                'Amount' => 250000,
                'OrderId' => 'inv-'.$invoice->id.'-abcdefgh',
                'RebillId' => 'rb-42',
                'Pan' => '430000******4242',
            ]),
        ]);

        $payload = ['TerminalKey' => 'test-terminal', 'OrderId' => 'inv-'.$invoice->id.'-abcdefgh', 'Success' => true, 'Status' => 'CONFIRMED', 'PaymentId' => 'pay-inv-2', 'Amount' => 250000];
        $payload['Token'] = app(\App\Services\TbankClient::class)->token($payload);
        $this->postJson('/api/tbank/webhook', $payload)->assertOk();

        $this->assertSame('paid', $invoice->fresh()->status);
        $sub = Subscription::where('user_id', $user->id)->firstOrFail();
        $this->assertTrue($sub->active);
        $this->assertSame(2500, $sub->amount);
        $this->assertSame(6, $sub->months);
        $this->assertTrue($sub->auto_pay);
        $this->assertSame('rb-42', $user->fresh()->tbank_rebill_id);
        $this->assertSame('4242', $user->fresh()->card_last4);
        $this->assertDatabaseHas('payments', ['user_id' => $user->id, 'kind' => 'invoice', 'amount' => 2500, 'invoice_id' => $invoice->id]);

        // Повторное уведомление ничего не меняет.
        $this->postJson('/api/tbank/webhook', $payload)->assertOk();
        $this->assertSame(1, Payment::count());
    }

    public function test_terms_change_updates_subscription_and_notifies(): void
    {
        $this->fakeFcm();
        $user = $this->user();
        DeviceToken::create(['user_id' => $user->id, 'token' => 'dev-2']);

        $this->asPanel()->putJson("/admin/api/users/{$user->id}/subscription", [
            'amount' => 990,
            'months' => 2,
            'until' => now()->addMonths(2)->toDateString(),
            'auto_pay' => true,
        ])->assertOk()->assertJsonPath('data.subscription.amount', 990);

        $this->assertDatabaseHas('subscriptions', ['user_id' => $user->id, 'amount' => 990, 'months' => 2, 'plan' => 'custom']);
        Http::assertSent(fn ($r) => str_contains($r->url(), 'fcm.googleapis.com') && $r['message']['notification']['title'] === 'Условия подписки изменены');
    }

    public function test_sales_summary_sums_payments_in_range(): void
    {
        $user = $this->user();
        Payment::create(['user_id' => $user->id, 'kind' => 'invoice', 'amount' => 1000, 'months' => 1, 'paid_at' => now()]);
        Payment::create(['user_id' => $user->id, 'kind' => 'renewal', 'amount' => 3000, 'months' => 3, 'paid_at' => now()]);
        Payment::create(['user_id' => $user->id, 'kind' => 'renewal', 'amount' => 500, 'months' => 1, 'paid_at' => now()->subYear()]);

        $this->asPanel()->getJson('/admin/api/analytics/sales?from='.now()->startOfMonth()->toDateString().'&to='.now()->toDateString())
            ->assertOk()
            ->assertJsonPath('summary.total', 4000)
            ->assertJsonPath('summary.count', 2)
            ->assertJsonPath('summary.average', 2000);
    }

    public function test_stats_endpoint_returns_blocks(): void
    {
        $this->asPanel()->getJson('/admin/api/analytics/stats')
            ->assertOk()
            ->assertJsonCount(8, 'registrations')
            ->assertJsonCount(3, 'subscriptions')
            ->assertJsonCount(6, 'revenue');
    }

    public function test_settings_store_documents_and_masked_keys(): void
    {
        $this->asPanel()->putJson('/admin/api/settings', [
            'default_price' => 1990,
            'documents' => [['title' => 'Соглашение', 'url' => 'https://example.com/terms']],
            'tradingview_api_key' => 'tv-secret-1234',
        ])->assertOk()
            ->assertJsonPath('data.default_price', 1990)
            ->assertJsonPath('data.tradingview_api_key', '•••• 1234');

        $this->getJson('/api/documents')->assertOk()
            ->assertJsonPath('data.0.title', 'Соглашение');

        // Маска не перезаписывает ключ.
        $this->asPanel()->putJson('/admin/api/settings', ['tradingview_api_key' => '•••• 1234'])->assertOk();
        $this->assertSame('tv-secret-1234', AppSetting::get('tradingview_api_key'));
    }

    public function test_documents_must_be_http_links(): void
    {
        $this->asPanel()->putJson('/admin/api/settings', [
            'documents' => [['title' => 'Плохо', 'url' => 'javascript:alert(1)']],
        ])->assertStatus(422);
    }

    public function test_draft_reports_are_hidden_from_app_and_toggle_from_panel(): void
    {
        $report = Report::firstOrFail();
        $report->update(['status' => 'actual']);

        $this->asPanel()->postJson("/admin/api/reports/{$report->id}/status", ['status' => 'draft'])
            ->assertOk()->assertJsonPath('data.status', 'draft');

        Sanctum::actingAs($this->user());
        $this->getJson('/api/reports')->assertOk()
            ->assertJsonMissing(['id' => $report->id]);
    }

    public function test_panel_admin_can_reply_in_direct_chat_and_user_gets_push(): void
    {
        $this->fakeFcm();
        $user = $this->user();
        DeviceToken::create(['user_id' => $user->id, 'token' => 'dev-3']);
        $chat = Chat::create([
            'slug' => 'dm-'.$user->id, 'title' => $user->name, 'type' => 'direct',
            'section' => 'direct', 'position' => 0, 'user_id' => $user->id,
        ]);

        $this->asPanel()->postJson("/admin/api/chats/{$chat->slug}/messages", ['body' => 'Здравствуйте'])
            ->assertCreated()
            ->assertJsonPath('data.admin', true)
            ->assertJsonPath('data.text', 'Здравствуйте');

        $this->assertDatabaseHas('messages', ['chat_id' => $chat->id, 'user_id' => null, 'body' => 'Здравствуйте']);
        Http::assertSent(fn ($r) => str_contains($r->url(), 'fcm.googleapis.com'));
    }

    public function test_broadcast_requires_configured_push(): void
    {
        config(['services.fcm.project_id' => null]);

        $this->asPanel()->postJson('/admin/api/push', [
            'title' => 'Привет', 'body' => 'Тест', 'audience' => 'all',
        ])->assertStatus(422);
    }

    public function test_broadcast_targets_subscribed_devices(): void
    {
        $this->fakeFcm();
        $paid = $this->user();
        Subscription::create(['user_id' => $paid->id, 'active' => true, 'until' => now()->addMonth()]);
        DeviceToken::create(['user_id' => $paid->id, 'token' => 'paid-dev']);
        DeviceToken::create(['user_id' => $this->user()->id, 'token' => 'free-dev']);

        $this->asPanel()->postJson('/admin/api/push', [
            'title' => 'Новости', 'body' => 'Для подписчиков', 'audience' => 'subscribed',
        ])->assertOk()->assertJson(['devices' => 1, 'sent' => 1]);
    }

    public function test_devices_register_and_unregister(): void
    {
        $user = $this->user();
        Sanctum::actingAs($user);

        $this->postJson('/api/devices', ['token' => 'tok-x', 'platform' => 'ios'])->assertCreated();
        $this->assertDatabaseHas('device_tokens', ['user_id' => $user->id, 'token' => 'tok-x', 'platform' => 'ios']);

        $this->deleteJson('/api/devices', ['token' => 'tok-x'])->assertOk();
        $this->assertDatabaseMissing('device_tokens', ['token' => 'tok-x']);
    }

    public function test_panel_telegram_admins_list_and_add(): void
    {
        $this->asPanel()->getJson('/admin/api/admins')->assertOk()->assertJsonPath('data.0.root', true);

        $this->asPanel()->postJson('/admin/api/admins', ['telegram_id' => '777', 'name' => 'Ирина'])
            ->assertCreated()
            ->assertJsonPath('data.telegram_id', '777');
    }

    public function test_panel_free_grant_opens_subscription_until_date(): void
    {
        $user = $this->user();
        $until = now()->addMonths(2)->toDateString();

        $this->asPanel()->postJson("/admin/api/users/{$user->id}/subscription/grant", ['until' => $until])
            ->assertOk()
            ->assertJsonPath('data.subscription.active', true)
            ->assertJsonPath('data.subscription.auto_pay', false)
            ->assertJsonPath('data.group', 'active');

        $this->assertDatabaseHas('subscriptions', ['user_id' => $user->id, 'plan' => 'gift', 'auto_pay' => false]);
        $this->assertDatabaseMissing('invoices', ['user_id' => $user->id]);

        Sanctum::actingAs($user);
        $this->getJson('/api/subscription')->assertOk()->assertJsonPath('data.active', true);

        $this->asPanel()->postJson("/admin/api/users/{$user->id}/subscription/grant", ['until' => now()->toDateString()])
            ->assertStatus(422);
    }

    public function test_free_grant_stops_granting_access_after_its_end_date(): void
    {
        $user = $this->user();
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/subscription/grant", ['until' => now()->addDay()->toDateString()])
            ->assertOk();

        $this->travel(2)->days();
        Sanctum::actingAs($user);
        $this->getJson('/api/subscription')->assertOk()->assertJsonPath('data.active', false);
    }
}
