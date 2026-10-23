<?php

namespace Tests\Feature;

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class BillingCardTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
        config([
            'services.yookassa.shop_id' => 'test-shop',
            'services.yookassa.secret_key' => 'test-secret',
            'services.yookassa.api_base' => 'https://api.yookassa.test/v3',
        ]);
    }

    private function demo(): User
    {
        return User::where('email', 'demo@karta.app')->first();
    }

    public function test_card_is_unbound_by_default(): void
    {
        Sanctum::actingAs($this->demo());

        $this->getJson('/api/billing/card')
            ->assertOk()
            ->assertExactJson(['bound' => false, 'brand' => null, 'last4' => null]);
    }

    public function test_bind_returns_confirmation_url_and_saves_payment_with_method(): void
    {
        Http::fake([
            'api.yookassa.test/v3/payments' => Http::response([
                'id' => 'pay-1',
                'confirmation' => ['type' => 'redirect', 'confirmation_url' => 'https://yoomoney.test/confirm'],
            ]),
        ]);
        Sanctum::actingAs($this->demo());

        $this->postJson('/api/billing/card/bind')
            ->assertOk()
            ->assertJson(['confirmation_url' => 'https://yoomoney.test/confirm']);

        Http::assertSent(function ($request) {
            $body = $request->data();

            return $request->url() === 'https://api.yookassa.test/v3/payments'
                && $request->hasHeader('Idempotence-Key')
                && $body['save_payment_method'] === true
                && $body['amount']['value'] === '1.00'
                && $body['amount']['currency'] === 'RUB'
                && $body['confirmation']['type'] === 'redirect'
                && $body['metadata']['purpose'] === 'card_bind';
        });
    }

    public function test_webhook_binds_card_after_fetching_payment_and_refunds(): void
    {
        $user = $this->demo();
        Http::fake([
            'api.yookassa.test/v3/payments/pay-1' => Http::response([
                'id' => 'pay-1',
                'status' => 'succeeded',
                'amount' => ['value' => '1.00', 'currency' => 'RUB'],
                'metadata' => ['purpose' => 'card_bind', 'user_id' => (string) $user->id],
                'payment_method' => [
                    'id' => 'pm-1', 'saved' => true, 'type' => 'bank_card',
                    'card' => ['card_type' => 'MasterCard', 'last4' => '4242'],
                ],
            ]),
            'api.yookassa.test/v3/refunds' => Http::response(['id' => 'ref-1', 'status' => 'succeeded']),
        ]);

        $payload = ['type' => 'notification', 'event' => 'payment.succeeded', 'object' => ['id' => 'pay-1']];
        $this->postJson('/api/yookassa/webhook', $payload)->assertNoContent();

        $user->refresh();
        $this->assertSame('pm-1', $user->yookassa_payment_method_id);
        $this->assertSame('4242', $user->card_last4);
        $this->assertSame('MasterCard', $user->card_brand);
        $this->assertNotNull($user->card_bound_at);

        Http::assertSent(fn ($request) => $request->url() === 'https://api.yookassa.test/v3/refunds'
            && $request->hasHeader('Idempotence-Key', 'refund-bind-pay-1'));
    }

    public function test_repeated_webhook_does_not_refund_twice(): void
    {
        $user = $this->demo();
        Http::fake([
            'api.yookassa.test/v3/payments/pay-2' => Http::response([
                'id' => 'pay-2', 'status' => 'succeeded',
                'amount' => ['value' => '1.00', 'currency' => 'RUB'],
                'metadata' => ['purpose' => 'card_bind', 'user_id' => (string) $user->id],
                'payment_method' => ['id' => 'pm-2', 'saved' => true, 'card' => ['card_type' => 'Visa', 'last4' => '1111']],
            ]),
            'api.yookassa.test/v3/refunds' => Http::response(['id' => 'ref-2']),
        ]);

        $payload = ['event' => 'payment.succeeded', 'object' => ['id' => 'pay-2']];
        $this->postJson('/api/yookassa/webhook', $payload)->assertNoContent();
        $this->postJson('/api/yookassa/webhook', $payload)->assertNoContent();

        Http::assertSentCount(3); // два запроса платежа и один возврат
    }

    public function test_webhook_ignores_events_that_are_not_bind_payments(): void
    {
        Http::fake([
            'api.yookassa.test/v3/payments/pay-3' => Http::response([
                'id' => 'pay-3', 'status' => 'succeeded',
                'metadata' => ['purpose' => 'other'],
            ]),
        ]);

        $this->postJson('/api/yookassa/webhook', ['event' => 'payment.succeeded', 'object' => ['id' => 'pay-3']])
            ->assertNoContent();

        $this->assertNull($this->demo()->fresh()->yookassa_payment_method_id);
        Http::assertNotSent(fn ($request) => str_contains($request->url(), '/refunds'));
    }

    public function test_unbind_clears_card_fields(): void
    {
        $user = $this->demo();
        $user->forceFill([
            'yookassa_payment_method_id' => 'pm-9', 'card_brand' => 'Visa',
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
        Http::fake([
            'api.yookassa.test/v3/payments' => Http::response([
                'id' => 'pay-late',
                'confirmation' => ['type' => 'redirect', 'confirmation_url' => 'https://yoomoney.test/late'],
            ]),
        ]);
        Sanctum::actingAs($this->demo());
        $this->postJson('/api/billing/card/bind')->assertOk();

        Http::fake([
            'api.yookassa.test/v3/payments/pay-late' => Http::response([
                'id' => 'pay-late',
                'status' => 'succeeded',
                'amount' => ['value' => '1.00', 'currency' => 'RUB'],
                'metadata' => ['purpose' => 'card_bind', 'user_id' => (string) $this->demo()->id],
                'payment_method' => ['id' => 'pm-late', 'saved' => true, 'card' => ['card_type' => 'MasterCard', 'last4' => '4242']],
            ]),
            'api.yookassa.test/v3/refunds' => Http::response(['id' => 'refund-late']),
            'api.yookassa.test/v3/payments' => Http::response(['id' => 'pay-charge', 'status' => 'pending']),
        ]);

        $this->getJson('/api/billing/card')
            ->assertOk()
            ->assertExactJson(['bound' => true, 'brand' => 'MasterCard', 'last4' => '4242']);
    }
}
