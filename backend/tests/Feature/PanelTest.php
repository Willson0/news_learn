<?php

namespace Tests\Feature;

use App\Models\AppSetting;
use App\Models\Chat;
use App\Models\DeviceToken;
use App\Models\Instrument;
use App\Models\Invoice;
use App\Models\PanelAdmin;
use App\Models\Payment;
use App\Models\Report;
use App\Models\Subscription;
use App\Models\User;
use App\Services\InvoiceService;
use App\Services\SubscriptionBilling;
use App\Services\TbankClient;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Carbon;
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
    private function fakeFcm(?\Closure $fcm = null): void
    {
        $key = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
        openssl_pkey_export($key, $pem);
        $path = storage_path('framework/testing/fcm-'.uniqid().'.json');
        @mkdir(dirname($path), 0777, true);
        file_put_contents($path, json_encode(['client_email' => 'svc@test.iam', 'private_key' => $pem]));

        config(['services.fcm.project_id' => 'karta-test', 'services.fcm.credentials' => $path]);
        Http::fake([
            'oauth2.googleapis.com/token' => Http::response(['access_token' => 'tok']),
            'fcm.googleapis.com/*' => $fcm ?? Http::response(['name' => 'message']),
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
            'password' => 'brand-new-pass-1',
            'password_confirmation' => 'brand-new-pass-1',
            'current_password' => 'secret-pass',
        ])->assertOk()->assertJson(['login' => 'new-boss']);

        $this->assertTrue(Hash::check('brand-new-pass-1', $this->panel->fresh()->password));
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

    public function test_invoice_sets_user_price_and_pushes_without_payment(): void
    {
        $this->fakeFcm();
        $user = $this->user();
        DeviceToken::create(['user_id' => $user->id, 'token' => 'dev-1', 'platform' => 'android']);
        Http::fake([
            'oauth2.googleapis.com/token' => Http::response(['access_token' => 'tok']),
            'fcm.googleapis.com/*' => Http::response(['name' => 'message']),
        ]);

        // Цена обязательна: «Выставить счёт» теперь — просто вписать цену подписки.
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", [])->assertStatus(422);

        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['amount' => 1700])
            ->assertCreated()
            ->assertJsonPath('data.amount', 1700)
            ->assertJsonPath('data.months', 1);

        // Платёж в Т-Кассе создаётся только когда пользователь нажмёт «Оплатить».
        Http::assertNotSent(fn ($r) => str_contains($r->url(), 'tbank'));
        Http::assertSent(fn ($r) => str_contains($r->url(), 'fcm.googleapis.com')
            && $r['message']['token'] === 'dev-1'
            && $r['message']['data']['type'] === 'subscription');

        $this->assertDatabaseHas('invoices', ['user_id' => $user->id, 'amount' => 1700, 'months' => 1, 'status' => 'pending', 'created_by' => 'boss']);
        $this->assertDatabaseHas('subscriptions', ['user_id' => $user->id, 'amount' => 1700, 'months' => 1]);

        // Админ выбирает и срок: цена — за этот срок, на него же идёт автопродление.
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['amount' => 4500, 'months' => 3])
            ->assertCreated()->assertJsonPath('data.months', 3);
        $this->assertDatabaseHas('subscriptions', ['user_id' => $user->id, 'amount' => 4500, 'months' => 3]);
        $this->assertSame(1, Invoice::where('user_id', $user->id)->where('status', 'pending')->count());

        Sanctum::actingAs($user);
        $this->getJson('/api/subscription')->assertOk()
            ->assertJsonPath('data.price', '4 500 ₽ / 3 мес.')
            ->assertJsonPath('data.can_pay', true);
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

        // Пользователь нажимает «Оплатить»: создаётся платёж по счёту с сохранением карты.
        Sanctum::actingAs($user);
        $this->postJson('/api/billing/card/bind')->assertOk()
            ->assertJson(['confirmation_url' => 'https://tbank.test/inv-2', 'invoice' => $invoice->id]);
        Http::assertSent(fn ($r) => str_ends_with($r->url(), '/Init')
            && $r['Amount'] === 250000 && $r['Recurrent'] === 'Y'
            && str_starts_with($r['OrderId'], 'inv-'.$invoice->id.'-'));

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
        $payload['Token'] = app(TbankClient::class)->token($payload);
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

    public function test_invoice_with_custom_days_period(): void
    {
        $user = $this->user();

        // Свой срок в днях вместо 1/3/6/12 месяцев; неверный срок отклоняется.
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['amount' => 900, 'days' => 0])->assertStatus(422);
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['amount' => 900, 'days' => 5000])->assertStatus(422);
        $this->asPanel()->postJson("/admin/api/users/{$user->id}/invoices", ['amount' => 900, 'days' => 45])
            ->assertCreated()
            ->assertJsonPath('data.days', 45)
            ->assertJsonPath('data.period', '45 дней');
        $this->assertDatabaseHas('invoices', ['user_id' => $user->id, 'amount' => 900, 'days' => 45, 'months' => 0, 'status' => 'pending']);
        $this->assertDatabaseHas('subscriptions', ['user_id' => $user->id, 'amount' => 900, 'days' => 45]);

        Sanctum::actingAs($user);
        $this->getJson('/api/subscription')->assertOk()
            ->assertJsonPath('data.price', '900 ₽ / 45 дн.')
            ->assertJsonPath('data.invoice.days', 45);

        // Оплата счёта продлевает подписку ровно на 45 дней.
        $invoice = Invoice::firstOrFail();
        app(InvoiceService::class)->markPaid($invoice, ['PaymentId' => 'pay-days-1']);
        $sub = Subscription::where('user_id', $user->id)->firstOrFail();
        $this->assertSame(
            Carbon::parse(Subscription::today())->addDays(45)->toDateString(),
            $sub->until->toDateString(),
        );
        $this->assertDatabaseHas('payments', ['user_id' => $user->id, 'kind' => 'invoice', 'days' => 45]);

        // Автопродление идёт на тот же срок в днях.
        $until = $sub->until->toDateString();
        app(SubscriptionBilling::class)->markPaid($user->fresh(), 'pay-days-2');
        $this->assertSame(
            Carbon::parse($until)->addDays(45)->toDateString(),
            $sub->fresh()->until->toDateString(),
        );

        // Переход обратно на месяцы сбрасывает дни.
        $this->asPanel()->putJson("/admin/api/users/{$user->id}/subscription", ['months' => 2])
            ->assertOk()->assertJsonPath('data.subscription.period', '2 месяца');
        $this->assertDatabaseHas('subscriptions', ['user_id' => $user->id, 'months' => 2, 'days' => null]);
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
        // Личный чат создаётся вместе с аккаунтом.
        $chat = Chat::where('type', 'direct')->where('user_id', $user->id)->firstOrFail();

        $this->asPanel()->postJson("/admin/api/chats/{$chat->slug}/messages", ['body' => 'Здравствуйте'])
            ->assertCreated()
            ->assertJsonPath('data.admin', true)
            ->assertJsonPath('data.text', 'Здравствуйте');

        $this->assertDatabaseHas('messages', ['chat_id' => $chat->id, 'user_id' => null, 'body' => 'Здравствуйте']);
        Http::assertSent(fn ($r) => str_contains($r->url(), 'fcm.googleapis.com'));
    }

    public function test_broadcast_without_fcm_is_not_blocked_and_warns(): void
    {
        config(['services.fcm.project_id' => null]);
        DeviceToken::create(['user_id' => $this->user()->id, 'token' => 'a-dev', 'platform' => 'android']);

        $this->asPanel()->postJson('/admin/api/push', [
            'title' => 'Привет', 'body' => 'Тест', 'audience' => 'all',
        ])->assertOk()
            ->assertJson(['devices' => 1, 'sent' => 0, 'configured' => false])
            ->assertJsonPath('warnings.0', fn ($w) => str_contains($w, 'ключ Firebase'));
    }

    public function test_broadcast_sends_to_android_and_warns_when_apns_is_missing(): void
    {
        $this->fakeFcm(function ($request) {
            // iOS-токен: в Firebase не загружен APNs-ключ Apple.
            if (($request->data()['message']['token'] ?? '') === 'ios-dev') {
                return Http::response(['error' => [
                    'code' => 401, 'status' => 'UNAUTHENTICATED', 'message' => 'Auth error from APNS or Web Push Service',
                    'details' => [['@type' => 'type.googleapis.com/google.firebase.fcm.v1.FcmError', 'errorCode' => 'THIRD_PARTY_AUTH_ERROR']],
                ]], 401);
            }

            return Http::response(['name' => 'message']);
        });
        DeviceToken::create(['user_id' => $this->user()->id, 'token' => 'android-dev', 'platform' => 'android']);
        DeviceToken::create(['user_id' => $this->user()->id, 'token' => 'ios-dev', 'platform' => 'ios']);

        $res = $this->asPanel()->postJson('/admin/api/push', [
            'title' => 'Привет', 'body' => 'Тест', 'audience' => 'all',
        ])->assertOk()
            ->assertJson(['devices' => 2, 'sent' => 1, 'android' => ['devices' => 1, 'sent' => 1], 'ios' => ['devices' => 1, 'sent' => 0]]);
        $this->assertTrue(collect($res->json('warnings'))->contains(fn ($w) => str_contains($w, 'Apple')));
        // Android-сообщение уходит с высоким приоритетом в канал приложения.
        Http::assertSent(fn ($r) => str_contains($r->url(), 'fcm.googleapis.com')
            && ($r->data()['message']['android']['notification']['channel_id'] ?? null) === 'karta_default');
    }

    public function test_firebase_key_can_be_uploaded_in_settings(): void
    {
        $key = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
        openssl_pkey_export($key, $pem);
        $json = json_encode(['type' => 'service_account', 'project_id' => 'karta-admin', 'client_email' => 'svc@karta.iam', 'private_key' => $pem]);
        config(['services.fcm.project_id' => null]);

        $this->asPanel()->putJson('/admin/api/settings', ['fcm_credentials' => '{"type":"other"}'])
            ->assertStatus(422)->assertJsonValidationErrors('fcm_credentials');

        $this->asPanel()->putJson('/admin/api/settings', ['fcm_credentials' => $json])->assertOk()
            ->assertJsonPath('data.fcm_configured', true)
            ->assertJsonPath('data.fcm_source', 'admin')
            ->assertJsonPath('data.fcm_project_id', 'karta-admin')
            ->assertJsonMissingPath('data.fcm_credentials');
        // В базе ключ зашифрован.
        $this->assertStringNotContainsString('PRIVATE KEY', (string) AppSetting::find('fcm_credentials')->value);

        Http::fake([
            'oauth2.googleapis.com/token' => Http::response(['access_token' => 'tok']),
            'fcm.googleapis.com/*' => Http::response(['name' => 'message']),
        ]);
        DeviceToken::create(['user_id' => $this->user()->id, 'token' => 'a-dev', 'platform' => 'android']);
        $this->asPanel()->postJson('/admin/api/push', ['title' => 'Т', 'body' => 'Т', 'audience' => 'all'])
            ->assertOk()->assertJson(['sent' => 1]);
        Http::assertSent(fn ($r) => str_contains($r->url(), 'projects/karta-admin/messages:send'));

        $this->asPanel()->putJson('/admin/api/settings', ['fcm_clear' => true])->assertOk()
            ->assertJsonPath('data.fcm_configured', false);
    }

    public function test_mail_settings_are_stored_in_admin_and_applied(): void
    {
        $this->asPanel()->putJson('/admin/api/settings', ['mail' => ['mailer' => 'smtp']])
            ->assertStatus(422)->assertJsonValidationErrors(['mail.host', 'mail.port', 'mail.from_address']);

        $this->asPanel()->putJson('/admin/api/settings', ['mail' => [
            'mailer' => 'smtp', 'host' => 'smtp.yandex.ru', 'port' => 465, 'encryption' => 'ssl',
            'username' => 'robot@karta.ru', 'password' => 'app-pass-123',
            'from_address' => 'robot@karta.ru', 'from_name' => 'Карта ликвидности',
        ]])->assertOk()
            ->assertJsonPath('data.mail.mailer', 'smtp')
            ->assertJsonPath('data.mail.host', 'smtp.yandex.ru')
            ->assertJsonPath('data.mail.password_set', true)
            ->assertJsonMissingPath('data.mail.password');
        $this->assertStringNotContainsString('app-pass-123', (string) AppSetting::find('mail_password')->value);

        $this->assertSame('smtp', config('mail.default'));
        $this->assertSame('smtps', config('mail.mailers.smtp.scheme'));
        $this->assertSame('app-pass-123', config('mail.mailers.smtp.password'));
        $this->assertSame('robot@karta.ru', config('mail.from.address'));

        // Пустой пароль не стирает сохранённый.
        $this->asPanel()->putJson('/admin/api/settings', ['mail' => [
            'mailer' => 'smtp', 'host' => 'smtp.mail.ru', 'port' => 587, 'encryption' => 'tls',
            'from_address' => 'robot@karta.ru',
        ]])->assertOk()->assertJsonPath('data.mail.password_set', true);
        $this->assertSame('app-pass-123', config('mail.mailers.smtp.password'));
        $this->assertSame('smtp', config('mail.mailers.smtp.scheme'));

        // Resend идёт через SMTP-шлюз с API-ключом вместо пароля.
        $this->asPanel()->putJson('/admin/api/settings', [
            'email_api_key' => 're_test_key',
            'mail' => ['mailer' => 'resend', 'from_address' => 'no-reply@karta.ru'],
        ])->assertOk();
        $this->assertSame('smtp.resend.com', config('mail.mailers.smtp.host'));
        $this->assertSame('re_test_key', config('mail.mailers.smtp.password'));
    }

    public function test_mail_test_endpoint_reports_disabled_mail(): void
    {
        $this->asPanel()->putJson('/admin/api/settings', ['mail' => ['mailer' => 'log']])->assertOk();
        $this->asPanel()->postJson('/admin/api/settings/mail/test', ['email' => 'me@karta.ru'])
            ->assertOk()->assertJson(['ok' => false]);
    }

    public function test_panel_chat_view_shows_images_and_previews(): void
    {
        $user = $this->user(['name' => 'Matvei']);
        $chat = Chat::where('type', 'direct')->where('user_id', $user->id)->firstOrFail();
        $msg = $chat->messages()->create(['user_id' => $user->id, 'author_name' => 'Matvei', 'body' => null]);
        $msg->attachments()->create(['kind' => 'image', 'path' => 'chat/p.jpg', 'name' => 'p.jpg', 'mime' => 'image/jpeg', 'size' => 1000, 'width' => 10, 'height' => 10]);

        $this->asPanel()->getJson("/admin/api/chats/{$chat->slug}/messages")->assertOk()
            ->assertJsonPath('data.0.attachments.0.kind', 'image')
            ->assertJsonPath('data.0.attachments.0.url', fn ($url) => str_contains($url, '/api/chats/attachments/') && str_contains($url, 'signature='));

        $this->asPanel()->getJson('/admin/api/chats/direct')->assertOk()
            ->assertJsonFragment(['preview' => 'Фото']);
    }

    public function test_new_report_pushes_subscribers_by_their_notification_settings(): void
    {
        $this->fakeFcm();
        $gold = Instrument::where('key', 'gold')->firstOrFail();
        $other = Instrument::where('key', '!=', 'gold')->firstOrFail();

        $all = $this->user(['notify_all' => true]);
        $byGold = $this->user(['notify_all' => false, 'notify_by_instrument' => true]);
        $byGold->instruments()->sync([$gold->id]);
        $byOther = $this->user(['notify_all' => false, 'notify_by_instrument' => true]);
        $byOther->instruments()->sync([$other->id]);
        $muted = $this->user(['notify_all' => false, 'notify_by_instrument' => false]);
        $free = $this->user(['notify_all' => true]);
        foreach ([$all, $byGold, $byOther, $muted] as $u) {
            Subscription::create(['user_id' => $u->id, 'active' => true, 'until' => now()->addMonth()]);
        }
        foreach (['all' => $all, 'gold' => $byGold, 'other' => $byOther, 'muted' => $muted, 'free' => $free] as $name => $u) {
            DeviceToken::create(['user_id' => $u->id, 'token' => "tok-{$name}", 'platform' => 'android']);
        }

        $this->asPanel()->post('/admin/api/reports', [
            'title' => 'Золото: новый отчёт', 'description' => 'Коротко', 'instrument' => 'gold',
            'cover' => UploadedFile::fake()->image('c.jpg'),
        ], ['Accept' => 'application/json'])->assertCreated();

        $sentTo = collect(Http::recorded())
            ->filter(fn ($pair) => str_contains($pair[0]->url(), 'fcm.googleapis.com'))
            ->map(fn ($pair) => $pair[0]->data()['message']['token'])->sort()->values()->all();
        $this->assertSame(['tok-all', 'tok-gold'], $sentTo);

        $report = Report::latest('id')->first();
        $this->assertNotNull($report->notified_at);

        // Повторная публикация того же отчёта второй push не шлёт.
        $before = count(Http::recorded());
        $this->asPanel()->postJson("/admin/api/reports/{$report->id}/status", ['status' => 'draft'])->assertOk();
        $this->asPanel()->postJson("/admin/api/reports/{$report->id}/status", ['status' => 'actual'])->assertOk();
        $this->assertSame($before, count(Http::recorded()));
    }

    public function test_reports_feed_is_newest_first_within_the_same_day(): void
    {
        Report::query()->delete();
        $admin = $this->user();
        Subscription::create(['user_id' => $admin->id, 'active' => true, 'until' => now()->addMonth()]);
        $instrument = Instrument::firstOrFail();
        $make = fn (string $title, string $created) => Report::forceCreate([
            'title' => $title, 'description' => 'd', 'instrument_id' => $instrument->id,
            'status' => 'actual', 'published_at' => today(), 'created_at' => $created, 'updated_at' => $created,
        ]);
        $make('Утро', today()->setTime(9, 0)->toDateTimeString());
        $make('Вечер', today()->setTime(18, 0)->toDateTimeString());
        $make('Обед', today()->setTime(13, 0)->toDateTimeString());
        Report::forceCreate([
            'title' => 'Вчера', 'description' => 'd', 'instrument_id' => $instrument->id,
            'status' => 'actual', 'published_at' => today()->subDay(),
        ]);

        Sanctum::actingAs($admin);
        $titles = collect($this->getJson('/api/reports')->assertOk()->json('data'))->pluck('title')->all();
        $this->assertSame(['Вечер', 'Обед', 'Утро', 'Вчера'], $titles);
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

    public function test_broadcast_treats_expired_subscription_as_free(): void
    {
        $this->fakeFcm();
        $expired = $this->user();
        // Флаг остался включённым, но срок вышел: это уже не подписчик.
        Subscription::create(['user_id' => $expired->id, 'active' => true, 'auto_pay' => true, 'until' => today()->subDays(4)]);
        DeviceToken::create(['user_id' => $expired->id, 'token' => 'expired-dev']);

        $this->asPanel()->postJson('/admin/api/push', [
            'title' => 'Новости', 'body' => 'Для подписчиков', 'audience' => 'subscribed',
        ])->assertOk()->assertJson(['devices' => 0]);
        $this->asPanel()->postJson('/admin/api/push', [
            'title' => 'Акция', 'body' => 'Для всех без подписки', 'audience' => 'free',
        ])->assertOk()->assertJson(['devices' => 1]);
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
