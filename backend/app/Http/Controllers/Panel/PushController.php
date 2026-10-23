<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\DeviceToken;
use App\Services\PushService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

/**
 * Рассылка push-уведомлений пользователям по аудитории.
 */
class PushController extends Controller
{
    public function __construct(private PushService $push) {}

    public function broadcast(Request $request): JsonResponse
    {
        $data = $request->validate([
            'title' => ['required', 'string', 'max:120'],
            'body' => ['required', 'string', 'max:500'],
            'audience' => ['required', 'in:all,subscribed,free'],
        ]);

        if (! $this->push->configured()) {
            throw ValidationException::withMessages(['title' => 'Push не настроен: задайте FCM_PROJECT_ID и FCM_CREDENTIALS.']);
        }

        $tokens = DeviceToken::query()
            ->when($data['audience'] === 'subscribed', fn ($q) => $q->whereHas('user.subscription', fn ($s) => $s->where('active', true)))
            ->when($data['audience'] === 'free', fn ($q) => $q->whereDoesntHave('user.subscription', fn ($s) => $s->where('active', true)))
            ->pluck('token')
            ->unique()
            ->values()
            ->all();

        $sent = $this->push->sendToTokens($tokens, $data['title'], $data['body'], ['type' => 'broadcast']);

        return response()->json(['devices' => count($tokens), 'sent' => $sent]);
    }
}
