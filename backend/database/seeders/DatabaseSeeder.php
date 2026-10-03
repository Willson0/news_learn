<?php

namespace Database\Seeders;

use App\Models\Chat;
use App\Models\Instrument;
use App\Models\Report;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        $instruments = [
            ['key' => 'gold', 'label' => 'Золото', 'category' => 'metal'],
            ['key' => 'silver', 'label' => 'Серебро', 'category' => 'metal'],
            ['key' => 'platinum', 'label' => 'Платина', 'category' => 'metal'],
            ['key' => 'wti', 'label' => 'Нефть WTI', 'category' => 'oil'],
            ['key' => 'brent', 'label' => 'Нефть Brent', 'category' => 'oil'],
            ['key' => 'usd', 'label' => 'USD', 'category' => 'currency'],
            ['key' => 'eur', 'label' => 'EUR', 'category' => 'currency'],
            ['key' => 'eurusd', 'label' => 'EUR/USD', 'category' => 'currency'],
            ['key' => 'gas', 'label' => 'Натуральный газ', 'category' => 'gas'],
            ['key' => 'btc', 'label' => 'Биткоин', 'category' => 'crypto'],
        ];
        foreach ($instruments as $i => $data) {
            Instrument::updateOrCreate(['key' => $data['key']], $data + ['position' => $i]);
        }

        $oil = Instrument::where('key', 'wti')->first();
        $gold = Instrument::where('key', 'gold')->first();
        $btc = Instrument::where('key', 'btc')->first();
        $gas = Instrument::where('key', 'gas')->first();

        $reports = [
            [
                'instrument_id' => $oil->id,
                'title' => 'Нефть в 2026 году, как она?',
                'description' => 'Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее',
                'badge' => 'Актуальный',
                'status' => 'actual',
                'published_at' => '2026-02-12',
            ],
            [
                'instrument_id' => $gold->id,
                'title' => 'Золото: тихая гавань или пузырь?',
                'description' => 'Разбираем динамику золота и что ждёт драгоценные металлы в ближайшие месяцы',
                'badge' => 'Актуальный',
                'status' => 'actual',
                'published_at' => '2026-02-10',
            ],
            [
                'instrument_id' => $btc->id,
                'title' => 'Биткоин после халвинга',
                'description' => 'Что происходит с криптовалютой и стоит ли ждать нового максимума',
                'badge' => null,
                'status' => 'hot',
                'published_at' => '2026-02-05',
            ],
            [
                'instrument_id' => $gas->id,
                'title' => 'Природный газ: прогноз',
                'description' => 'Что ждёт рынок природного газа в начале года',
                'badge' => null,
                'status' => 'inactual',
                'published_at' => '2026-02-08',
            ],
        ];
        foreach ($reports as $data) {
            Report::updateOrCreate(
                ['title' => $data['title']],
                $data + ['body' => $data['description']]
            );
        }

        $chats = [
            ['slug' => 'admin', 'title' => 'Игорь Гломозда', 'type' => 'admin', 'section' => 'admin', 'subtitle' => 'Был в сети 1 час назад', 'position' => 0],
            ['slug' => 'general', 'title' => 'Общий чат', 'type' => 'group', 'section' => 'main', 'subtitle' => '234 участника', 'position' => 1],
            ['slug' => 'gold', 'title' => 'Отчет по золоту 26.02.26', 'type' => 'report', 'section' => 'reports', 'subtitle' => '12 участников', 'pinned' => true, 'position' => 2],
            ['slug' => 'oil', 'title' => 'Нефть 12.12.27', 'type' => 'report', 'section' => 'reports', 'subtitle' => '15 участников', 'position' => 3],
            ['slug' => 'platinum', 'title' => 'Отчет по платине 26.02.26', 'type' => 'report', 'section' => 'reports', 'subtitle' => '8 участников', 'position' => 4],
        ];
        foreach ($chats as $data) {
            Chat::updateOrCreate(['slug' => $data['slug']], $data);
        }

        $admin = Chat::where('slug', 'admin')->first();
        if ($admin->messages()->count() === 0) {
            $admin->messages()->createMany([
                ['author_name' => 'Игорь Гломозда', 'author_role' => 'Автор', 'body' => 'Здравствуйте! Чем могу помочь?'],
                ['author_name' => 'Игорь Гломозда', 'author_role' => 'Автор', 'body' => 'Что интересует?'],
            ]);
        }

        $oilChat = Chat::where('slug', 'oil')->first();
        if ($oilChat->messages()->count() === 0) {
            $oilChat->messages()->createMany([
                ['author_name' => 'Артем', 'author_role' => 'Участник', 'body' => 'Здравствуте! Помогите разобраться с графиком'],
                ['author_name' => 'Владимир', 'author_role' => 'Участник', 'body' => 'Видно, что отчёт сделан хорошо и всё объяснено'],
            ]);
        }

        // Демо-пользователь для входа по email/паролю.
        $user = User::updateOrCreate(
            ['email' => 'demo@karta.app'],
            [
                'name' => 'Артем',
                'username' => '@AlexeyRub',
                'phone' => '+7 999 999 99 99',
                'password' => Hash::make('password'),
            ]
        );

        $user->instruments()->sync(
            Instrument::whereIn('key', ['gold', 'platinum', 'brent'])->pluck('id')
        );

        Subscription::updateOrCreate(
            ['user_id' => $user->id],
            [
                'active' => true,
                'plan' => '1300 руб. месяц',
                'until' => '2026-09-14',
                'auto_pay' => true,
                'payment_method' => 'СБП 1488',
            ]
        );
    }
}
