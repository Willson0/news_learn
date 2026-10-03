<?php

namespace App\Http\Controllers;

use App\Models\Subscription;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class SubscriptionController extends Controller
{
    public function show(Request $request): JsonResponse
    {
        $sub = $request->user()->subscription;

        return response()->json([
            'data' => $sub ? [
                'active' => $sub->active,
                'plan' => $sub->plan,
                'until' => $sub->until?->format('d.m.Y'),
                'auto_pay' => $sub->auto_pay,
                'payment_method' => $sub->payment_method,
            ] : null,
        ]);
    }

    /**
     * Изменить автоплатёж / привязанный способ оплаты (экран подписки).
     */
    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'auto_pay' => ['sometimes', 'boolean'],
            'payment_method' => ['sometimes', 'nullable', 'string', 'max:255'],
        ]);

        $sub = Subscription::firstOrCreate(['user_id' => $request->user()->id]);
        $sub->fill($data)->save();

        return response()->json(['status' => 'ok']);
    }
}
