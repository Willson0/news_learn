<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\DeviceToken;
use App\Services\PushService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

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

        $devices = DeviceToken::query()
            ->when($data['audience'] === 'subscribed', fn ($q) => $q->whereHas('user.subscription', fn ($s) => $s->grantingAccess()))
            ->when($data['audience'] === 'free', fn ($q) => $q->whereDoesntHave('user.subscription', fn ($s) => $s->grantingAccess()))
            ->get(['token', 'platform']);

        // Рассылку не блокируем: что не настроено — показываем предупреждением, остальное отправляем.
        $result = $this->push->sendToDevices($devices, $data['title'], $data['body'], ['type' => 'broadcast']);
        $count = fn (string $platform, string $field) => $result['platforms'][$platform][$field] ?? 0;

        $warnings = [];
        if (! $result['configured']) {
            $warnings[] = 'Push не настроен: не загружен ключ Firebase (Настройки → Push-уведомления, '
                .'или FCM_PROJECT_ID и FCM_CREDENTIALS в .env). Без него Google не принимает уведомления '
                .'ни для Android, ни для iOS, поэтому ничего не отправлено.';
        }
        if ($result['configured'] && $result['apns_missing']) {
            $warnings[] = 'Для Apple (iOS) push не настроен: в консоли Firebase не загружен APNs-ключ. '
                .'На iPhone уведомления не ушли, на Android отправлены.';
        }
        if ($result['configured'] && $devices->isNotEmpty() && $devices->where('platform', 'ios')->isEmpty()) {
            $warnings[] = 'В выбранной аудитории нет iPhone с включёнными уведомлениями.';
        }
        if ($devices->isEmpty()) {
            $warnings[] = 'В выбранной аудитории нет устройств с включёнными уведомлениями.';
        }

        return response()->json([
            'devices' => $devices->count(),
            'sent' => array_sum(array_column($result['platforms'], 'sent')),
            'android' => ['devices' => $count('android', 'devices'), 'sent' => $count('android', 'sent')],
            'ios' => ['devices' => $count('ios', 'devices'), 'sent' => $count('ios', 'sent')],
            'configured' => $result['configured'],
            'warnings' => $warnings,
            'errors' => $result['errors'],
        ]);
    }
}
