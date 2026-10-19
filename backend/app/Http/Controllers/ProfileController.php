<?php

namespace App\Http\Controllers;

use App\Models\Instrument;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;

class ProfileController extends Controller
{
    private const DISK = 'local';

    /**
     * Данные профиля: пользователь, подписка, отслеживаемые инструменты,
     * настройки уведомлений.
     */
    public function show(Request $request): JsonResponse
    {
        $user = $request->user()->load(['instruments', 'subscription']);

        return response()->json([
            'user' => $this->userPayload($user),
            'subscription' => $this->subscriptionPayload($user),
            'notifications' => [
                'all' => $user->notify_all,
                'by_instrument' => $user->notify_by_instrument,
            ],
            'instruments' => $this->instrumentsPayload($user),
        ]);
    }

    /**
     * Редактирование профиля (имя, тег).
     */
    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['sometimes', 'string', 'max:255'],
            'username' => ['sometimes', 'nullable', 'string', 'max:255',
                \Illuminate\Validation\Rule::unique('users', 'username')->ignore($request->user()->id)],
        ], [
            'username.unique' => 'Тег уже занят',
        ]);

        $request->user()->update($data);

        return response()->json(['status' => 'ok']);
    }

    /**
     * Загрузка/смена фото профиля (multipart, поле avatar).
     * Файл кладётся на диск, avatar_url указывает на публичный роут отдачи.
     */
    public function updateAvatar(Request $request): JsonResponse
    {
        $request->validate([
            'avatar' => ['required', 'image', 'max:5120'],
        ]);

        $user = $request->user();
        $path = $request->file('avatar')->store('avatars', self::DISK);

        $this->deleteAvatarFile($user->avatar_path);

        $user->update([
            'avatar_path' => $path,
            'avatar_url' => "/users/{$user->id}/avatar?v=".now()->timestamp,
        ]);

        return response()->json(['data' => $this->userPayload($user)]);
    }

    /**
     * Удалить фото профиля.
     */
    public function deleteAvatar(Request $request): JsonResponse
    {
        $user = $request->user();
        $this->deleteAvatarFile($user->avatar_path);
        $user->update(['avatar_path' => null, 'avatar_url' => null]);

        return response()->json(['data' => $this->userPayload($user)]);
    }

    private function deleteAvatarFile(?string $path): void
    {
        if ($path && Storage::disk(self::DISK)->exists($path)) {
            Storage::disk(self::DISK)->delete($path);
        }
    }

    /**
     * @return array<string, mixed>
     */
    private function userPayload($user): array
    {
        return [
            'id' => $user->id,
            'name' => $user->name,
            'email' => $user->email,
            'phone' => $user->phone,
            'username' => $user->username,
            'avatar_url' => $user->avatar_url,
        ];
    }

    /**
     * Сохранить набор отслеживаемых инструментов (переключатели в профиле
     * и в настройках уведомлений).
     */
    public function syncInstruments(Request $request): JsonResponse
    {
        $data = $request->validate([
            'instruments' => ['present', 'array'],
            'instruments.*' => ['string'],
        ]);

        $ids = Instrument::whereIn('key', $data['instruments'])->pluck('id');
        $request->user()->instruments()->sync($ids);

        return response()->json([
            'instruments' => $this->instrumentsPayload($request->user()->load('instruments')),
        ]);
    }

    /**
     * Настройки push-уведомлений.
     */
    public function updateNotifications(Request $request): JsonResponse
    {
        $data = $request->validate([
            'all' => ['sometimes', 'boolean'],
            'by_instrument' => ['sometimes', 'boolean'],
        ]);

        $request->user()->update([
            'notify_all' => $data['all'] ?? $request->user()->notify_all,
            'notify_by_instrument' => $data['by_instrument'] ?? $request->user()->notify_by_instrument,
        ]);

        return response()->json(['status' => 'ok']);
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function instrumentsPayload($user): array
    {
        $tracked = $user->instruments->pluck('key')->all();

        return Instrument::orderBy('position')->get()
            ->map(fn (Instrument $i) => [
                'key' => $i->key,
                'label' => $i->label,
                'on' => in_array($i->key, $tracked, true),
            ])->all();
    }

    /**
     * @return array<string, mixed>|null
     */
    private function subscriptionPayload($user): ?array
    {
        $sub = $user->subscription;
        if (! $sub) {
            return null;
        }

        return [
            'active' => $sub->active,
            'plan' => $sub->plan,
            'until' => $sub->until?->format('d.m.Y'),
            'auto_pay' => $sub->auto_pay,
            'payment_method' => $sub->payment_method,
        ];
    }
}
