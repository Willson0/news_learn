<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Admin;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

/**
 * Управление списком админов. Админа назначают по почте пользователя.
 * Добавлять и удалять админов могут только админы; главного админа (config/admin.php) удалить нельзя.
 */
class AdminController extends Controller
{
    public function index(): JsonResponse
    {
        $rootId = Admin::rootTelegramId();
        $list = [$this->toArray(null, $rootId, null, User::where('telegram_id', $rootId)->first(), true)];

        Admin::with('user')->orderBy('created_at')->get()->each(function (Admin $admin) use (&$list) {
            $user = $admin->user ?? ($admin->telegram_id !== null ? User::where('telegram_id', $admin->telegram_id)->first() : null);
            $list[] = $this->toArray($admin->id, $admin->telegram_id, $admin->name, $user, false);
        });

        return response()->json(['data' => $list]);
    }

    /**
     * Назначает админа по почте. Старый вариант с telegram_id принимается, чтобы уже установленные
     * приложения продолжали работать.
     */
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['nullable', 'string', 'email', 'max:255'],
            'telegram_id' => ['nullable', 'regex:/^\d{1,20}$/'],
            'name' => ['nullable', 'string', 'max:255'],
        ], [
            'email.email' => 'Укажите корректную почту',
            'telegram_id.regex' => 'Telegram ID состоит только из цифр',
        ]);

        $email = trim((string) ($data['email'] ?? ''));
        $telegramId = trim((string) ($data['telegram_id'] ?? ''));

        if ($email === '' && $telegramId === '') {
            throw ValidationException::withMessages(['email' => 'Укажите почту пользователя']);
        }

        if ($email !== '') {
            $target = $this->findByEmail($email);
            if (! $target) {
                throw ValidationException::withMessages([
                    'email' => 'Пользователь с почтой '.mb_strtolower($email).' не найден. Он должен зарегистрироваться в приложении.',
                ]);
            }
        } else {
            $target = User::where('telegram_id', $telegramId)->first();
        }

        if ($target ? Admin::grantsAccess($target) : Admin::hasTelegramRow($telegramId)) {
            throw ValidationException::withMessages([$email !== '' ? 'email' : 'telegram_id' => 'Этот пользователь уже админ']);
        }

        $admin = Admin::create([
            'user_id' => $target?->id,
            'telegram_id' => $target?->telegram_id ?? ($telegramId !== '' ? $telegramId : null),
            'name' => $data['name'] ?? null,
            'added_by' => $request->user() instanceof User ? $request->user()->id : null,
        ]);

        return response()->json([
            'data' => $this->toArray($admin->id, $admin->telegram_id, $admin->name, $target, false),
        ], 201);
    }

    public function destroy(Admin $admin): JsonResponse
    {
        if (Admin::isRoot($admin->telegram_id)) {
            return response()->json(['message' => 'Главного админа удалить нельзя.'], 403);
        }

        $admin->delete();

        return response()->json(['status' => 'ok']);
    }

    /** Почту сравниваем без учёта регистра. */
    private function findByEmail(string $email): ?User
    {
        return User::whereRaw('LOWER(email) = ?', [mb_strtolower(trim($email))])->first();
    }

    /**
     * @return array<string, mixed>
     */
    private function toArray(?int $id, int|string|null $telegramId, ?string $name, ?User $user, bool $root): array
    {
        return [
            'id' => $id,
            'telegram_id' => $telegramId !== null ? (string) $telegramId : null,
            'name' => $user?->name ?? $name,
            'email' => $user?->email,
            'username' => $user?->username,
            'root' => $root,
        ];
    }
}
