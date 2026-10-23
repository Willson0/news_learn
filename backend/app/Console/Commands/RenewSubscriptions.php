<?php

namespace App\Console\Commands;

use App\Services\SubscriptionBilling;
use Illuminate\Console\Command;

class RenewSubscriptions extends Command
{
    protected $signature = 'subscriptions:renew';

    protected $description = 'Списать продление подписок с сохранённых карт';

    public function handle(SubscriptionBilling $billing): int
    {
        $this->info('Попыток списания: '.$billing->renewDue());

        return self::SUCCESS;
    }
}
