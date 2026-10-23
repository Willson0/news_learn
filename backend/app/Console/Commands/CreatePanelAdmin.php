<?php

namespace App\Console\Commands;

use App\Models\PanelAdmin;
use Illuminate\Console\Command;

class CreatePanelAdmin extends Command
{
    protected $signature = 'panel:create {login} {password}';

    protected $description = 'Создать учётку веб-админки (логин и пароль)';

    public function handle(): int
    {
        $password = (string) $this->argument('password');
        if (mb_strlen($password) < 8) {
            $this->error('Пароль должен быть не короче 8 символов.');

            return self::FAILURE;
        }

        PanelAdmin::updateOrCreate(
            ['login' => (string) $this->argument('login')],
            ['password' => $password],
        );

        $this->info('Учётка веб-админки сохранена.');

        return self::SUCCESS;
    }
}
