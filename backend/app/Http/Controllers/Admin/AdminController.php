<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Admin;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Управление списком админов. Добавлять и удалять админов могут только админы;
 * главного админа (config/admin.php) удалить нельзя.
 */
class AdminController extends Controller
{
    public function index(): JsonResponse
    {
        $rootId = Admin::rootTelegramId();
        $users = User::whereNotNull('telegram_id')->get()->keyBy('telegram_id');

        $list = [$this->toArray($rootId, null, $users->get($rootId), true)];

        Admin::orderBy('created_at')->get()->each(function (Admin $admin) use (&$list, $users) {
            $list[] = $this->toArray($admin->telegram_id, $admin->name, $users->get($admin->telegram_id), false);
        });

        return response()->json(['data' => $list]);
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'telegram_id' => [
                'required', 'regex:/^\d{1,20}$/',
                Rule::unique('admins', 'telegram_id'),
            ],
            'name' => ['nullable', 'string', 'max:255'],
        ], [
            'telegram_id.required' => 'Укажите Telegram ID',
            'telegram_id.regex' => 'Telegram ID состоит только из цифр',
            'telegram_id.unique' => 'Этот пользователь уже админ',
        ]);

        if (Admin::isRoot($data['telegram_id'])) {
            throw ValidationException::withMessages(['telegram_id' => 'Этот пользователь уже админ']);
        }

        $admin = Admin::create([
            'telegram_id' => $data['telegram_id'],
            'name' => $data['name'] ?? null,
            'added_by' => $request->user()->id,
        ]);

        $user = User::where('telegram_id', $admin->telegram_id)->first();

        return response()->json(['data' => $this->toArray($admin->telegram_id, $admin->name, $user, false)], 201);
    }

    public function destroy(string $telegramId): JsonResponse
    {
        if (Admin::isRoot($telegramId)) {
            return response()->json(['message' => 'Главного админа удалить нельзя.'], 403);
        }

        Admin::where('telegram_id', $telegramId)->firstOrFail()->delete();

        return response()->json(['status' => 'ok']);
    }

    /**
     * @return array<string, mixed>
     */
    private function toArray(int|string $telegramId, ?string $name, ?User $user, bool $root): array
    {
        return [
            'telegram_id' => (string) $telegramId,
            'name' => $user?->name ?? $name,
            'username' => $user?->username,
            'root' => $root,
        ];
    }
}
