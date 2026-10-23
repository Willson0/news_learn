<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\AppSetting;
use App\Services\PushService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Настройки приложения: цена по умолчанию, документы, ключи сервисов.
 * Секреты наружу отдаются только в замаскированном виде.
 */
class SettingsController extends Controller
{
    public function show(): JsonResponse
    {
        return response()->json(['data' => [
            'default_price' => AppSetting::defaultPrice(),
            'default_months' => AppSetting::defaultMonths(),
            'documents' => AppSetting::get('documents', []),
            'tradingview_api_key' => $this->mask(AppSetting::get('tradingview_api_key')),
            'email_api_key' => $this->mask(AppSetting::get('email_api_key')),
            'fcm_configured' => app(PushService::class)->configured(),
        ]]);
    }

    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'default_price' => ['sometimes', 'integer', 'min:1', 'max:1000000'],
            'default_months' => ['sometimes', 'integer', 'min:1', 'max:12'],
            'documents' => ['sometimes', 'array', 'max:30'],
            'documents.*.title' => ['required_with:documents', 'string', 'max:120'],
            'documents.*.url' => ['required_with:documents', 'url:http,https', 'max:2048'],
            'tradingview_api_key' => ['sometimes', 'nullable', 'string', 'max:255'],
            'email_api_key' => ['sometimes', 'nullable', 'string', 'max:255'],
        ], [
            'documents.*.url.url' => 'Ссылка документа должна начинаться с http:// или https://',
        ]);

        if (isset($data['default_price'])) {
            AppSetting::put('default_price', $data['default_price']);
        }
        if (isset($data['default_months'])) {
            AppSetting::put('default_months', $data['default_months']);
        }
        if (array_key_exists('documents', $data)) {
            AppSetting::put('documents', array_values($data['documents']));
        }
        // Секреты: пришла маска или пусто — значение не трогаем, пустое поле очищает ключ.
        foreach (['tradingview_api_key', 'email_api_key'] as $key) {
            if (! array_key_exists($key, $data) || str_starts_with((string) $data[$key], '•')) {
                continue;
            }
            AppSetting::put($key, $data[$key] ?: null);
        }

        return $this->show();
    }

    private function mask(?string $secret): ?string
    {
        if (! $secret) {
            return null;
        }

        return '•••• '.substr($secret, -4);
    }
}
