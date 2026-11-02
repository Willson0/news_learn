<?php

namespace App\Support;

use App\Models\AppSetting;
use App\Models\Invoice;
use App\Models\User;

/**
 * Состояние подписки для приложений — одно и то же в /subscription и /profile.
 */
class SubscriptionPayload
{
    /** @return array<string, mixed> */
    public static function for(User $user): array
    {
        $sub = $user->subscription;
        $active = (bool) $sub?->grantsAccessNow();
        $invoice = $active ? null : Invoice::pendingFor($user);
        $tariff = (bool) $sub?->tariffDefined();

        // Стоимость показываем, только когда она известна: по выставленному счёту или по
        // назначенному тарифу. Иначе null — приложения пишут «Не определен» (макет 484:13055).
        $price = match (true) {
            $invoice !== null => self::label($invoice->amount, $invoice->months),
            $tariff => AppSetting::subscriptionPriceLabel($sub),
            default => null,
        };

        return [
            // Как в проверке доступа к отчётам: флаг active вместе с датой окончания.
            'active' => $active,
            'plan' => $sub?->plan,
            'until' => $sub?->until?->format('d.m.Y'),
            'auto_pay' => (bool) $sub?->auto_pay,
            'payment_method' => $sub?->payment_method,
            'price' => $price,
            // Можно ли сейчас оплатить: есть счёт или назначен тариф.
            'can_pay' => $active || $invoice !== null || $tariff,
            'invoice' => $invoice ? [
                'id' => $invoice->id,
                'amount' => $invoice->amount,
                'months' => $invoice->months,
            ] : null,
        ];
    }

    public static function label(int $amount, int $months): string
    {
        return number_format($amount, 0, ',', ' ').' ₽ / '.$months.' мес.';
    }
}
