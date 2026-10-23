<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\Chat;
use App\Models\Message;
use App\Services\PushService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Чаты в админке: общие чаты, личные чаты пользователей и ответы администратора.
 */
class ChatController extends Controller
{
    public function __construct(private PushService $push) {}

    /** Общие чаты и чаты по отчётам. */
    public function groups(): JsonResponse
    {
        $chats = Chat::whereIn('section', ['main', 'reports'])
            ->withCount('messages')
            ->orderBy('section')->orderBy('position')
            ->get();

        return response()->json(['data' => $chats->map(fn (Chat $c) => [
            'slug' => $c->slug,
            'title' => $c->title,
            'section' => $c->section,
            'type' => $c->type,
            'messages' => $c->messages_count,
        ])->values()]);
    }

    /** Личные чаты с пользователями, по последней активности. */
    public function direct(): JsonResponse
    {
        $chats = Chat::with(['owner', 'latestMessage'])
            ->where('type', 'direct')
            ->has('messages')
            ->get()
            ->sortByDesc(fn (Chat $c) => $c->latestMessage?->created_at)
            ->values();

        return response()->json(['data' => $chats->map(fn (Chat $c) => [
            'slug' => $c->slug,
            'title' => $c->owner?->name ?? $c->title,
            'username' => $c->owner?->username,
            'preview' => $c->latestMessage?->body,
            'time' => $c->latestMessage?->created_at?->format('d.m.Y H:i'),
        ])->values()]);
    }

    public function messages(string $slug): JsonResponse
    {
        $chat = Chat::where('slug', $slug)->firstOrFail();

        $messages = $chat->messages()->oldest()->limit(300)->get()->map(fn (Message $m) => [
            'id' => $m->id,
            'author' => $m->author_name,
            'role' => $m->author_role,
            'admin' => $m->user_id === null,
            'text' => $m->body,
            'time' => $m->created_at?->format('d.m.Y H:i'),
        ])->values();

        return response()->json([
            'chat' => ['slug' => $chat->slug, 'title' => $chat->title, 'type' => $chat->type],
            'data' => $messages,
        ]);
    }

    /**
     * Ответ администратора. В личном чате пользователь получает push.
     */
    public function send(Request $request, string $slug): JsonResponse
    {
        $chat = Chat::with('owner')->where('slug', $slug)->firstOrFail();

        $data = $request->validate([
            'body' => ['required', 'string', 'max:4000'],
        ]);

        $message = $chat->messages()->create([
            'user_id' => null,
            'author_name' => config('admin.display_name'),
            'author_role' => 'Админ',
            'body' => $data['body'],
        ]);

        if ($chat->type === 'direct' && $chat->owner) {
            $this->push->sendToUser($chat->owner, config('admin.display_name'), $data['body'], [
                'type' => 'chat',
                'chat' => 'admin',
            ]);
        }

        return response()->json(['data' => [
            'id' => $message->id,
            'author' => $message->author_name,
            'role' => $message->author_role,
            'admin' => true,
            'text' => $message->body,
            'time' => $message->created_at?->format('d.m.Y H:i'),
        ]], 201);
    }
}
