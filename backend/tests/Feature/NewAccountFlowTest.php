<?php

namespace Tests\Feature;

use App\Models\Admin;
use App\Models\Chat;
use App\Models\Invoice;
use App\Models\Message;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

/**
 * Новый аккаунт: личный чат с администрацией есть сразу, а подписка оплачивается
 * только после того, как админ определит тариф (выставит счёт или задаст условия).
 */
class NewAccountFlowTest extends TestCase
{
    use RefreshDatabase;

    private const API = 'https://api.tbank.test/v2';

    protected function setUp(): void
    {
        parent::setUp();
        config([
            'services.tbank.terminal_key' => 'test-terminal',
            'services.tbank.password' => 'test-pass',
            'services.tbank.api_base' => self::API,
            'admin.root_telegram_id' => 999,
        ]);
    }

    private function admin(): User
    {
        return User::factory()->create(['telegram_id' => 999]);
    }

    public function test_direct_chat_is_created_with_the_account(): void
    {
        $user = User::factory()->create();

        $this->assertDatabaseHas('chats', ['type' => 'direct', 'user_id' => $user->id, 'slug' => 'dm-'.$user->id]);
    }

    public function test_admin_sees_new_users_chat_before_any_message(): void
    {
        $admin = $this->admin();
        $quiet = User::factory()->create(['name' => 'Новый']);
        $talking = User::factory()->create(['name' => 'Пишет']);
        Message::create([
            'chat_id' => Chat::directFor($talking)->id, 'user_id' => $talking->id,
            'author_name' => $talking->name, 'body' => 'Здравствуйте',
        ]);

        Sanctum::actingAs($admin);
        $direct = collect($this->getJson('/api/chats')->assertOk()->json('data'))->firstWhere('key', 'direct');

        $titles = collect($direct['items'])->pluck('title')->all();
        // С перепиской — первыми, затем новые аккаунты; собственного пустого чата админа нет.
        $this->assertSame(['Пишет', 'Новый'], $titles);
    }

    public function test_panel_direct_list_includes_users_without_messages(): void
    {
        $user = User::factory()->create(['name' => 'Без сообщений']);
        $this->assertTrue(collect(Chat::directChatsForStaff())->contains(fn ($c) => $c->user_id === $user->id));
    }

    public function test_new_account_has_no_tariff_and_cannot_pay(): void
    {
        Http::fake();
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->getJson('/api/subscription')->assertOk()
            ->assertJsonPath('data.active', false)
            ->assertJsonPath('data.price', null)
            ->assertJsonPath('data.can_pay', false)
            ->assertJsonPath('data.invoice', null);
        $this->getJson('/api/profile')->assertOk()->assertJsonPath('subscription.price', null);

        $this->postJson('/api/billing/card/bind')
            ->assertStatus(409)
            ->assertJsonPath('code', 'tariff_not_defined');
        Http::assertNothingSent();
    }

    public function test_pending_invoice_defines_price_and_bind_opens_it(): void
    {
        Http::fake();
        $user = User::factory()->create();
        $invoice = Invoice::create([
            'user_id' => $user->id, 'amount' => 1300, 'months' => 1, 'status' => Invoice::PENDING,
            'tbank_payment_id' => 'inv-pay', 'confirmation_url' => 'https://tbank.test/invoice',
        ]);
        Sanctum::actingAs($user);

        $this->getJson('/api/subscription')->assertOk()
            ->assertJsonPath('data.price', '1 300 ₽ / 1 мес.')
            ->assertJsonPath('data.can_pay', true)
            ->assertJsonPath('data.invoice.id', $invoice->id);

        // «Привязать» ведёт на оплату счёта, новый платёж не создаётся.
        $this->postJson('/api/billing/card/bind')->assertOk()
            ->assertJson(['confirmation_url' => 'https://tbank.test/invoice']);
        Http::assertNothingSent();
    }

    public function test_assigned_terms_allow_paying_by_card(): void
    {
        Http::fake([
            self::API.'/Init' => Http::response(['Success' => true, 'Status' => 'NEW', 'PaymentId' => 'p1', 'PaymentURL' => 'https://tbank.test/bind']),
        ]);
        $user = User::factory()->create();
        Subscription::create(['user_id' => $user->id, 'active' => false, 'amount' => 2000, 'months' => 3]);
        Sanctum::actingAs($user);

        $this->getJson('/api/subscription')->assertOk()
            ->assertJsonPath('data.price', '2 000 ₽ / 3 мес.')
            ->assertJsonPath('data.can_pay', true);
        $this->postJson('/api/billing/card/bind')->assertOk()
            ->assertJson(['confirmation_url' => 'https://tbank.test/bind']);
    }

    public function test_card_bound_without_tariff_does_not_charge(): void
    {
        $user = User::factory()->create(['tbank_rebill_id' => null]);
        // Активный подарочный доступ: карту привязать можно, но тарифа нет.
        Subscription::create(['user_id' => $user->id, 'active' => true, 'plan' => 'gift', 'until' => now()->addDay()->toDateString()]);
        $user->subscription()->update(['active' => false]);
        $state = ['Success' => true, 'Status' => 'CONFIRMED', 'PaymentId' => '55', 'OrderId' => 'bind-'.$user->id.'-x', 'RebillId' => 'rb'];
        Http::fake([
            self::API.'/GetState' => Http::response($state),
            self::API.'/Cancel' => Http::response(['Success' => true, 'Status' => 'REFUNDED']),
        ]);
        $payload = ['TerminalKey' => 'test-terminal'] + $state;
        $payload['Token'] = app(\App\Services\TbankClient::class)->token($payload);

        $this->postJson('/api/tbank/webhook', $payload)->assertOk();

        $this->assertSame('rb', $user->fresh()->tbank_rebill_id);
        Http::assertNotSent(fn ($r) => $r->url() === self::API.'/Charge');
        Http::assertNotSent(fn ($r) => $r->url() === self::API.'/Init');
    }
}
