<?php

namespace App\Http\Controllers;

use App\Models\Chat;
use App\Models\Message;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class ChatController extends Controller
{
    /**
     * Список чатов, сгруппированный по секциям (админ / чаты / по отчётам).
     */
    public function index(): JsonResponse
    {
        $chats = Chat::orderBy('position')->get();

        $sectionTitles = [
            'admin' => 'Чат с админом',
            'main' => 'Чаты',
            'reports' => 'Чаты по отчетам',
        ];

        $sections = [];
        foreach (['admin', 'main', 'reports'] as $key) {
            $items = $chats->where('section', $key)->values();
            if ($items->isEmpty()) {
                continue;
            }
            $sections[] = [
                'key' => $key,
                'title' => $sectionTitles[$key] ?? $key,
                'items' => $items->map(fn (Chat $c) => [
                    'id' => $c->slug,
                    'title' => $c->title,
                    'subtitle' => $c->subtitle,
                    'pinned' => $c->pinned,
                ])->all(),
            ];
        }

        return response()->json(['data' => $sections]);
    }

    /**
     * Сообщения чата.
     */
    public function messages(string $slug): JsonResponse
    {
        $chat = Chat::where('slug', $slug)->firstOrFail();

        $messages = $chat->messages()->with('user')->oldest()->get()
            ->map(fn (Message $m) => $this->toArray($m));

        return response()->json([
            'chat' => [
                'id' => $chat->slug,
                'title' => $chat->title,
                'subtitle' => $chat->subtitle,
                'type' => $chat->type,
            ],
            'data' => $messages,
        ]);
    }

    /**
     * Отправить сообщение в чат.
     */
    public function send(Request $request, string $slug): JsonResponse
    {
        $chat = Chat::where('slug', $slug)->firstOrFail();

        $data = $request->validate([
            'body' => ['required', 'string', 'max:4000'],
        ]);

        $message = $chat->messages()->create([
            'user_id' => $request->user()->id,
            'author_name' => $request->user()->name,
            'body' => $data['body'],
        ]);

        return response()->json(['data' => $this->toArray($message->load('user'))], 201);
    }

    /**
     * @return array<string, mixed>
     */
    private function toArray(Message $m): array
    {
        return [
            'id' => $m->id,
            'mine' => $m->user_id !== null && $m->user_id === request()->user()?->id,
            'author' => $m->author_name,
            'role' => $m->author_role,
            'text' => $m->body,
            'time' => $m->created_at?->format('H:i'),
        ];
    }
}
