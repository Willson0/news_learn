<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\ChatController as AppChatController;
use App\Http\Controllers\Controller;
use App\Models\Chat;
use App\Models\Message;
use App\Models\MessageAttachment;
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

    /** Личные чаты со всеми пользователями: сначала по последней активности, затем новые без переписки. */
    public function direct(): JsonResponse
    {
        $chats = Chat::directChatsForStaff();

        return response()->json(['data' => $chats->map(fn (Chat $c) => [
            'slug' => $c->slug,
            'title' => $c->owner?->name ?? $c->title,
            'username' => $c->owner?->username,
            'preview' => $c->latestMessage ? self::previewOf($c->latestMessage) : null,
            'time' => $c->latestMessage?->created_at?->format('d.m.Y H:i'),
        ])->values()]);
    }

    public function messages(string $slug): JsonResponse
    {
        $chat = Chat::where('slug', $slug)->firstOrFail();

        // Последние 300 сообщений (а не первые), в хронологическом порядке.
        $messages = $chat->messages()
            ->with(['attachments', 'replyTo.attachments'])
            ->latest('id')->limit(300)->get()
            ->reverse()
            ->map(fn (Message $m) => self::present($m))
            ->values();

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

        return response()->json(['data' => self::present($message->load('attachments'))], 201);
    }

    /**
     * Сообщение для просмотра в админке: текст, вложения (фото, голосовые, файлы — по подписанным
     * ссылкам, как в приложении), цитата ответа и служебные «зашёл/вышел».
     *
     * @return array<string, mixed>
     */
    private static function present(Message $m): array
    {
        $attachments = $m->attachments->map(fn (MessageAttachment $a) => [
            'id' => $a->id,
            'kind' => $a->kind,
            'url' => AppChatController::fileUrl($a),
            'name' => $a->name,
            'size' => AppChatController::formatSize($a->size),
            'duration' => $a->kind === 'voice' ? AppChatController::formatDuration($a->duration) : null,
            'width' => $a->width,
            'height' => $a->height,
        ])->values()->all();

        $reply = $m->relationLoaded('replyTo') && $m->replyTo ? [
            'author' => $m->replyTo->author_name,
            'text' => self::previewOf($m->replyTo),
        ] : null;

        return [
            'id' => $m->id,
            'author' => $m->author_name,
            'role' => $m->author_role,
            'admin' => $m->user_id === null,
            'system' => $m->isSystem(),
            'private' => $m->isPrivate(),
            'text' => $m->isSystem() ? AppChatController::eventText($m) : $m->body,
            'attachments' => $attachments,
            'reply' => $reply,
            'time' => $m->created_at?->format('d.m.Y H:i'),
        ];
    }

    /** Короткий текст сообщения для списка чатов и цитат: у фото без подписи — «Фото» и т. п. */
    private static function previewOf(Message $m): string
    {
        if ($m->isSystem()) {
            return AppChatController::eventText($m);
        }
        if ((string) $m->body !== '') {
            return (string) $m->body;
        }
        $att = $m->attachments->first();

        return match ($att?->kind) {
            'image' => $m->attachments->count() > 1 ? 'Фото ('.$m->attachments->count().')' : 'Фото',
            'voice' => 'Голосовое сообщение',
            'file' => $att->name ?: 'Файл',
            default => 'Сообщение',
        };
    }
}
