<?php

namespace App\Http\Controllers;

use App\Models\Chat;
use App\Models\Message;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

class ChatController extends Controller
{
    /**
     * Список чатов, сгруппированный по секциям.
     *
     * Пользователь видит свой личный чат с админом (id «admin»), общие чаты и чаты по отчётам.
     * Админ вместо своего личного чата видит секцию «direct» — личные чаты всех пользователей
     * (вкладка «Личные чаты» на фронте).
     */
    public function index(Request $request): JsonResponse
    {
        $user = $request->user();
        $isAdmin = $user->isAdmin();

        $sections = [];

        if (! $isAdmin) {
            $sections[] = [
                'key' => 'admin',
                'title' => 'Чат с админом',
                'items' => [self::presentChat(self::directChatFor($user), $user)],
            ];
        }

        $chats = Chat::with('latestMessage')
            ->whereIn('section', ['main', 'reports'])
            ->orderByDesc('pinned')
            ->orderBy('position')
            ->get();

        foreach (['main' => 'Чаты', 'reports' => 'Чаты по отчетам'] as $key => $title) {
            $items = $chats->where('section', $key)->values();
            if ($items->isNotEmpty()) {
                $sections[] = [
                    'key' => $key,
                    'title' => $title,
                    'items' => $items->map(fn (Chat $c) => self::presentChat($c, $user))->all(),
                ];
            }
        }

        if ($isAdmin) {
            $direct = Chat::with(['latestMessage', 'owner'])
                ->where('type', 'direct')
                ->has('messages')
                ->get()
                ->sortByDesc(fn (Chat $c) => $c->latestMessage?->created_at)
                ->values();

            $sections[] = [
                'key' => 'direct',
                'title' => 'Чаты',
                'items' => $direct->map(fn (Chat $c) => self::presentChat($c, $user))->all(),
            ];
        }

        return response()->json(['data' => $sections]);
    }

    /**
     * Сообщения чата.
     */
    public function messages(Request $request, string $slug): JsonResponse
    {
        $chat = self::resolve($slug, $request->user());

        $messages = $chat->messages()->with('user')->oldest()->get()
            ->map(fn (Message $m) => $this->toArray($m, $request->user()));

        return response()->json([
            'chat' => self::presentChat($chat, $request->user()),
            'data' => $messages,
        ]);
    }

    /**
     * Отправить сообщение в чат.
     */
    public function send(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        $data = $request->validate([
            'body' => ['required', 'string', 'max:4000'],
        ]);

        $message = $chat->messages()->create([
            'user_id' => $user->id,
            'author_name' => $user->name,
            'author_role' => $user->isAdmin() ? 'Админ' : 'Участник',
            'body' => $data['body'],
        ]);

        return response()->json(['data' => $this->toArray($message->load('user'), $user)], 201);
    }

    /**
     * Фото чата (без токена — для <img>).
     */
    public function avatar(string $slug): BinaryFileResponse
    {
        $chat = Chat::where('slug', $slug)->firstOrFail();
        abort_unless($chat->avatar_path && Storage::disk('local')->exists($chat->avatar_path), 404);

        return response()->file(Storage::disk('local')->path($chat->avatar_path));
    }

    /**
     * Находит чат по slug с учётом доступа: «admin» — личный чат текущего пользователя,
     * чужой личный чат доступен только админам.
     */
    public static function resolve(string $slug, User $user): Chat
    {
        if ($slug === 'admin') {
            return self::directChatFor($user);
        }

        $chat = Chat::where('slug', $slug)->firstOrFail();

        if ($chat->type === 'direct' && $chat->user_id !== $user->id && ! $user->isAdmin()) {
            abort(404);
        }

        return $chat;
    }

    public static function directChatFor(User $user): Chat
    {
        return Chat::firstOrCreate(
            ['type' => 'direct', 'user_id' => $user->id],
            [
                'slug' => 'dm-'.$user->id,
                'title' => $user->name,
                'section' => 'direct',
                'position' => 0,
            ],
        );
    }

    /**
     * @return array<string, mixed>
     */
    public static function presentChat(Chat $chat, User $viewer): array
    {
        $direct = $chat->type === 'direct';
        $ownDirect = $direct && $chat->user_id === $viewer->id;
        $last = $chat->latestMessage;

        return [
            // Свой личный чат пользователь всегда открывает как /community/chat/admin.
            'id' => $ownDirect ? 'admin' : $chat->slug,
            'type' => $chat->type,
            // Пользователь видит в личном чате имя админа, админ — имя пользователя.
            'title' => $ownDirect ? config('admin.display_name') : ($direct ? ($chat->owner?->name ?? $chat->title) : $chat->title),
            'subtitle' => $direct && ! $ownDirect ? $chat->owner?->username : $chat->subtitle,
            'pinned' => $chat->pinned,
            'avatar_url' => $chat->avatar_path ? "/chats/{$chat->slug}/avatar?v={$chat->updated_at?->timestamp}" : null,
            'report' => $chat->report_id,
            'sender' => $last ? ($last->user_id === $viewer->id ? 'Вы' : $last->author_name) : null,
            'preview' => $last?->body,
            'time' => $last?->created_at?->format('H:i'),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function toArray(Message $m, User $viewer): array
    {
        return [
            'id' => $m->id,
            'mine' => $m->user_id !== null && $m->user_id === $viewer->id,
            'author' => $m->author_name,
            'role' => $m->author_role,
            'text' => $m->body,
            'time' => $m->created_at?->format('H:i'),
        ];
    }
}
