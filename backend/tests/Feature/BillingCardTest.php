<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\TbankClient;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class BillingCardTest extends TestCase
{
    use RefreshDatabase;

    private const API = 'https://api.tbank.test/v2';

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
        config([
            'services.tbank.terminal_key' => 'test-terminal',
            'services.tbank.password' => 'test-pass',
            'services.tbank.api_base' => self::API,
        ]);
    }

    private function demo(): User
    {
        return User::where('email', 'demo@karta.app')->first();
    }

    /** Подписывает уведомление так же, как это делает Т-Касса: тем же алгоритмом Token. */
    private function signed(array $payload): array
    {
        $payload['Token'] = app(TbankClient::class)->token($payload);

        return $payload;
    }

    private function notification(string $paymentId, string $orderId): array
    {
        return $this->signed([
            'TerminalKey' => 'test-terminal',
            'OrderId' => $orderId,
            'Success' => true,
            'Status' => 'CONFIRMED',
            'PaymentId' => $paymentId,
            'Amount' => 100,
        ]);
    }

    public function test_token_follows_documented_algorithm(): void
    {
        // Ключи по алфавиту (Amount, OrderId, Password, TerminalKey), значения склеиваются;
        // Token и вложенные объекты не учитываются.
        $params = ['TerminalKey' => 'TK', 'Amount' => 100, 'OrderId' => 'o-1', 'Token' => 'ignored', 'DATA' => ['x' => 1]];
        $expected = hash('sha256', '100'.'o-1'.'test-pass'.'TK');

        $this->assertSame($expected, app(TbankClient::class)->token($params));
    }

    public function test_card_is_unbound_by_default(): void
    {
        Sanctum::actingAs($this->demo());

        $this->getJson('/api/billing/card')
            ->assertOk()
            ->assertExactJson(['bound' => false, 'brand' => null, 'last4' => null]);
    }

    public function test_bind_starts_recurrent_payment_and_returns_payment_url(): void
    {
        Http::fake([
            self::API.'/Init' => Http::response([
                'Success' => true, 'Status' => 'NEW', 'PaymentId' => 'pay-1', 'PaymentURL' => 'https://tbank.test/confirm',
            ]),
        ]);
        Sanctum::actingAs($this->demo());

        $this->postJson('/api/billing/card/bind')
            ->assertOk()
            ->assertJson(['confirmation_url' => 'https://tbank.test/confirm']);

        Http::assertSent(function ($request) {
            $body = $request->data();

            return $request->url() === self::API.'/Init'
                && $body['TerminalKey'] === 'test-terminal'
                && $body['Amount'] === 100
                && $body['Recurrent'] === 'Y'
                && $body['CustomerKey'] === 'user-'.$this->demo()->id
                && str_starts_with($body['OrderId'], 'bind-'.$this->demo()->id.'-')
                && $body['Token'] === app(TbankClient::class)->token(collect($body)->except('Token')->all());
        });
    }

    public function test_webhook_binds_card_and_refunds_bind_amount(): void
    {
        $user = $this->demo();
        Http::fake([
            self::API.'/GetState' => Http::response([
                'Success' => true, 'Status' => 'CONFIRMED', 'PaymentId' => 'pay-1', 'Amount' => 100,
                'OrderId' => 'bind-'.$user->id.'-abc', 'RebillId' => 'rb-1', 'Pan' => '430000******4242',
            ]),
            self::API.'/Cancel' => Http::response(['Success' => true, 'Status' => 'REFUNDED', 'PaymentId' => 'pay-1']),
        ]);

        $this->postJson('/api/tbank/webhook', $this->notification('pay-1', 'bind-'.$user->id.'-abc'))
            ->assertOk()
            ->assertSeeText('OK', false);

        $user->refresh();
        $this->assertSame('rb-1', $user->tbank_rebill_id);
        $this->assertSame('4242', $user->card_last4);
        $this->assertNotNull($user->card_bound_at);

        Http::assertSent(fn ($request) => $request->url() === self::API.'/Cancel'
            && $request->data()['PaymentId'] === 'pay-1');
    }

    public function test_webhook_with_wrong_signature_is_refused(): void
    {
        Http::fake();
        $payload = $this->notification('pay-9', 'bind-'.$this->demo()->id.'-x');
        $payload['Token'] = 'forged';

        $this->postJson('/api/tbank/webhook', $payload)->assertForbidden();

        Http::assertNothingSent();
        $this->assertNull($this->demo()->fresh()->tbank_rebill_id);
    }

    public function test_repeated_webhook_does_not_refund_twice(): void
    {
        $user = $this->demo();
        Http::fake([
            self::API.'/GetState' => Http::response([
                'Success' => true, 'Status' => 'CONFIRMED', 'PaymentId' => 'pay-2',
                'OrderId' => 'bind-'.$user->id.'-def', 'RebillId' => 'rb-2', 'Pan' => '411111******1111',
            ]),
            self::API.'/Cancel' => Http::response(['Success' => true, 'Status' => 'REFUNDED']),
        ]);

        $payload = $this->notification('pay-2', 'bind-'.$user->id.'-def');
        $this->postJson('/api/tbank/webhook', $payload)->assertOk();
        $this->postJson('/api/tbank/webhook', $payload)->assertOk();

        Http::assertSentCount(3); // два запроса состояния платежа и один возврат
    }

    public function test_webhook_ignores_payments_without_known_purpose(): void
    {
        Http::fake([
            self::API.'/GetState' => Http::response([
                'Success' => true, 'Status' => 'CONFIRMED', 'PaymentId' => 'pay-3', 'OrderId' => 'other-1-zz', 'RebillId' => 'rb-3',
            ]),
        ]);

        $this->postJson('/api/tbank/webhook', $this->notification('pay-3', 'other-1-zz'))->assertOk();

        $this->assertNull($this->demo()->fresh()->tbank_rebill_id);
        Http::assertNotSent(fn ($request) => str_contains($request->url(), '/Cancel'));
    }

    public function test_unbind_clears_card_fields(): void
    {
        $user = $this->demo();
        $user->forceFill([
            'tbank_rebill_id' => 'rb-9', 'card_brand' => 'Visa',
            'card_last4' => '0001', 'card_bound_at' => now(),
        ])->save();
        Sanctum::actingAs($user);

        $this->deleteJson('/api/billing/card')->assertNoContent();

        $this->getJson('/api/billing/card')
            ->assertExactJson(['bound' => false, 'brand' => null, 'last4' => null]);
    }

    public function test_return_redirects_to_app_scheme(): void
    {
        $this->get('/api/billing/card/return?status=ok')
            ->assertRedirect('karta://billing?status=ok');
        $this->get('/api/billing/card/return?status=cancel')
            ->assertRedirect('karta://billing?status=error');
    }

    public function test_card_request_settles_pending_bind_when_webhook_is_late(): void
    {
        $user = $this->demo();
        Http::fake([
            self::API.'/Init' => Http::response([
                'Success' => true, 'Status' => 'NEW', 'PaymentId' => 'pay-late', 'PaymentURL' => 'https://tbank.test/late',
            ]),
        ]);
        Sanctum::actingAs($user);
        $this->postJson('/api/billing/card/bind')->assertOk();

        Http::fake([
            self::API.'/GetState' => Http::response([
                'Success' => true, 'Status' => 'CONFIRMED', 'PaymentId' => 'pay-late',
                'OrderId' => 'bind-'.$user->id.'-late', 'RebillId' => 'rb-late', 'Pan' => '530000******4242',
            ]),
            self::API.'/Cancel' => Http::response(['Success' => true, 'Status' => 'REFUNDED']),
        ]);

        $this->getJson('/api/billing/card')
            ->assertOk()
            ->assertExactJson(['bound' => true, 'brand' => null, 'last4' => '4242']);
    }
}
