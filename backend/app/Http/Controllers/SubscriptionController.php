<?php

namespace App\Http\Controllers;

use App\Models\AppSetting;
use App\Models\Subscription;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class SubscriptionController extends Controller
{
    public function show(Request $request): JsonResponse
    {
        $sub = $request->user()->subscription;

        return response()->json([
            'data' => [
                'active' => (bool) $sub?->grantsAccessNow(),
                'plan' => $sub?->plan,
                'until' => $sub?->until?->format('d.m.Y'),
                'auto_pay' => (bool) $sub?->auto_pay,
                'payment_method' => $sub?->payment_method,
                'price' => AppSetting::subscriptionPriceLabel($sub),
            ],
        ]);
    }

    /**
     * Изменить автоплатёж / привязанный способ оплаты (экран подписки).
     */
    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'auto_pay' => ['sometimes', 'boolean'],
            'payment_method' => ['sometimes', 'nullable', 'string', 'max:64'],
        ]);

        // Пользователь меняет только свои настройки уже существующей подписки: статус, срок и сумму
        // задают оплата и админ. Новую запись отсюда не создаём.
        $sub = Subscription::where('user_id', $request->user()->id)->first();
        $sub?->fill($data)->save();

        return response()->json(['status' => 'ok']);
    }
}
