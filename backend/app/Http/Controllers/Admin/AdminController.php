<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Admin;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
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

    /**
     * Назначает админа по тегу (username из профиля, например @ivan). Старый вариант с telegram_id
     * оставлен, чтобы уже установленные приложения продолжали работать.
     */
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'tag' => ['nullable', 'string', 'max:65', 'regex:/^@?[\p{L}\p{N}_.]{1,64}$/u'],
            'telegram_id' => ['nullable', 'regex:/^\d{1,20}$/'],
            'name' => ['nullable', 'string', 'max:255'],
        ], [
            'tag.regex' => 'Тег может содержать буквы, цифры, «_» и «.»',
            'telegram_id.regex' => 'Telegram ID состоит только из цифр',
        ]);

        $tag = trim((string) ($data['tag'] ?? ''));
        $telegramId = trim((string) ($data['telegram_id'] ?? ''));

        if ($tag === '' && $telegramId === '') {
            throw ValidationException::withMessages(['tag' => 'Укажите тег пользователя']);
        }

        $field = $tag !== '' ? 'tag' : 'telegram_id';
        if ($tag !== '') {
            $found = $this->findByTag($tag);
            if (! $found) {
                throw ValidationException::withMessages([
                    'tag' => 'Пользователь с тегом '.$this->normalizeTag($tag).' не найден. Тег указан в профиле в приложении.',
                ]);
            }
            if ($found->telegram_id === null) {
                throw ValidationException::withMessages([
                    'tag' => 'У этого пользователя нет Telegram-аккаунта, назначить его админом нельзя.',
                ]);
            }
            $telegramId = (string) $found->telegram_id;
        }

        if (Admin::hasTelegramId($telegramId)) {
            throw ValidationException::withMessages([$field => 'Этот пользователь уже админ']);
        }

        $admin = Admin::create([
            'telegram_id' => $telegramId,
            'name' => $data['name'] ?? null,
            'added_by' => $request->user() instanceof User ? $request->user()->id : null,
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

    /** Тег в базе хранится как «@ник», а вводить его можно и с «@», и без. */
    private function normalizeTag(string $tag): string
    {
        return '@'.ltrim(trim($tag), '@');
    }

    /** Ищем без учёта регистра; если тегом пользуются несколько записей, берём с Telegram ID. */
    private function findByTag(string $tag): ?User
    {
        $handle = mb_strtolower(ltrim(trim($tag), '@'));

        return User::whereRaw('LOWER(username) IN (?, ?)', ['@'.$handle, $handle])
            ->orderByRaw('telegram_id IS NULL')
            ->first();
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
