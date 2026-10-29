<?php

namespace Tests\Feature;

use App\Models\PanelAdmin;
use App\Models\Subscription;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Laravel\Sanctum\Sanctum;
use PHPUnit\Framework\Attributes\DataProvider;
use Tests\TestCase;

/**
 * Одно правило «подписка активна» (флаг active и дата окончания не раньше сегодняшней по Москве)
 * должно давать одинаковый ответ в профиле, на экране подписки, в ленте отчётов и в админке.
 */
class SubscriptionStatusTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(DatabaseSeeder::class);
    }

    protected function tearDown(): void
    {
        Carbon::setTestNow();
        parent::tearDown();
    }

    /** @return array<string, array{array<string, mixed>, string, bool}> */
    public static function cases(): array
    {
        return [
            // Случай из жалобы: «до 25.10», сегодня 29.10, автопродление включено.
            'истекла, автопродление' => [['active' => true, 'auto_pay' => true, 'until' => '2026-10-25'], '2026-10-29 12:00', false],
            'истекла вчера' => [['active' => true, 'auto_pay' => false, 'until' => '2026-10-28'], '2026-10-29 12:00', false],
            'день окончания включительно' => [['active' => true, 'auto_pay' => true, 'until' => '2026-10-29'], '2026-10-29 20:00', true],
            // 25.10 21:30 UTC — уже 26.10 по Москве.
            'полночь по Москве' => [['active' => true, 'auto_pay' => true, 'until' => '2026-10-25'], '2026-10-25 21:30', false],
            'до полуночи по Москве' => [['active' => true, 'auto_pay' => true, 'until' => '2026-10-25'], '2026-10-25 20:30', true],
            'выключена, дата в будущем' => [['active' => false, 'auto_pay' => true, 'until' => '2026-11-29'], '2026-10-29 12:00', false],
            'без даты окончания' => [['active' => true, 'auto_pay' => false, 'until' => null], '2026-10-29 12:00', false],
            'активна' => [['active' => true, 'auto_pay' => true, 'until' => '2026-11-29'], '2026-10-29 12:00', true],
        ];
    }

    /** @param  array<string, mixed>  $terms */
    #[DataProvider('cases')]
    public function test_status_is_the_same_everywhere(array $terms, string $now, bool $active): void
    {
        $user = User::factory()->create(['name' => 'Проверка Подписки']);
        Subscription::create(['user_id' => $user->id, 'amount' => 1490, 'months' => 1] + $terms);
        Carbon::setTestNow(Carbon::parse($now, 'UTC'));

        $this->assertSame($active, $user->fresh()->subscription->grantsAccessNow());
        $this->assertSame($active, $user->fresh()->hasAccess());
        $this->assertSame($active, Subscription::grantingAccess()->where('user_id', $user->id)->exists());

        Sanctum::actingAs($user);
        $this->getJson('/api/profile')->assertOk()->assertJsonPath('subscription.active', $active);
        $this->getJson('/api/subscription')->assertOk()->assertJsonPath('data.active', $active);
        $reports = $this->getJson('/api/reports')->assertOk()->json('data');
        $active ? $this->assertNotEmpty($reports) : $this->assertSame([], $reports);

        $panel = PanelAdmin::create(['login' => 'boss', 'password' => 'secret-pass']);
        $group = $active ? 'active' : ($terms['until'] ? 'expired' : 'free');
        $this->actingAs($panel, 'panel')->getJson('/admin/api/users/'.$user->id)->assertOk()
            ->assertJsonPath('data.group', $group)
            ->assertJsonPath('data.subscription.access_now', $active);
        $this->actingAs($panel, 'panel')->getJson('/admin/api/users?filter=active&q=Проверка')->assertOk()
            ->assertJsonCount($active ? 1 : 0, 'data');
    }
}
