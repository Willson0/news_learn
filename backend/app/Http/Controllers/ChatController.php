<?php

namespace App\Http\Controllers;

use App\Models\Chat;
use App\Models\ChatRead;
use App\Models\Message;
use App\Models\MessageAttachment;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\URL;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

class ChatController extends Controller
{
    private const DISK = 'local';

    /**
     * Список чатов, сгруппированный по секциям.
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
            ->whereNotIn('id', $user->leftChats()->select('chats.id'))
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
     * Сообщения чата + мета (закреп, участники). Помечает чат прочитанным.
     */
    public function messages(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        $messages = $chat->messages()
            ->with(['user', 'replyTo.user', 'attachments', 'reactions'])
            ->oldest()
            ->get();

        // Прочитано другими: максимальный last_read среди остальных участников.
        $othersRead = (int) ChatRead::where('chat_id', $chat->id)
            ->where('user_id', '!=', $user->id)
            ->max('last_read_message_id');

        $payload = $messages->map(fn (Message $m) => $this->toArray($m, $user, $othersRead))->all();

        // Отмечаем чат прочитанным текущим пользователем.
        $lastId = (int) ($messages->last()->id ?? 0);
        if ($lastId > 0) {
            ChatRead::updateOrCreate(
                ['chat_id' => $chat->id, 'user_id' => $user->id],
                ['last_read_message_id' => $lastId],
            );
        }

        $pinned = $messages->firstWhere('pinned', true);

        return response()->json([
            'chat' => self::presentChat($chat, $user),
            'pinned' => $pinned ? $this->quoteOf($pinned) : null,
            'data' => $payload,
        ]);
    }

    /**
     * Отправить сообщение: текст и/или вложение (image|file|voice), опционально ответ.
     */
    public function send(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        $data = $request->validate([
            'body' => ['nullable', 'string', 'max:4000'],
            'reply_to' => ['nullable', 'integer'],
            'attachment' => ['nullable', 'file', 'max:102400'],
            // Несколько фото в одном сообщении (как в Телеграме): attachments[] одного вида.
            'attachments' => ['nullable', 'array', 'max:10'],
            'attachments.*' => ['file', 'max:102400'],
            'kind' => ['nullable', 'in:image,file,voice', 'required_with:attachment,attachments'],
            'duration' => ['nullable', 'integer', 'min:0', 'max:36000'],
        ]);

        $files = $request->file('attachments', []);
        if ($request->hasFile('attachment')) {
            $files[] = $request->file('attachment');
        }

        $hasBody = isset($data['body']) && trim($data['body']) !== '';
        if (! $hasBody && $files === []) {
            throw ValidationException::withMessages([
                'body' => 'Сообщение пустое.',
            ]);
        }

        // Ответ допустим только на сообщение этого же чата.
        $replyToId = null;
        if (! empty($data['reply_to'])) {
            $replyToId = optional(
                $chat->messages()->whereKey($data['reply_to'])->first()
            )->id;
        }

        $message = DB::transaction(function () use ($chat, $user, $data, $hasBody, $replyToId, $files) {
            $message = $chat->messages()->create([
                'user_id' => $user->id,
                'reply_to_id' => $replyToId,
                'author_name' => $user->name,
                'author_role' => $user->isAdmin() ? 'Админ' : 'Участник',
                'body' => $hasBody ? trim($data['body']) : null,
            ]);

            foreach ($files as $file) {
                $this->storeAttachment($message, $file, $data['kind'], $data['duration'] ?? null);
            }

            return $message;
        });

        $message->load(['user', 'replyTo.user', 'attachments', 'reactions']);

        return response()->json(['data' => $this->toArray($message, $user)], 201);
    }

    /**
     * Поставить/снять реакцию на сообщение.
     */
    public function react(Request $request, string $slug, int $message): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $msg = $chat->messages()->whereKey($message)->firstOrFail();

        $data = $request->validate([
            'emoji' => ['required', 'string', 'max:16'],
        ]);

        $existing = $msg->reactions()
            ->where('user_id', $user->id)
            ->where('emoji', $data['emoji'])
            ->first();

        if ($existing) {
            $existing->delete();
        } else {
            $msg->reactions()->create(['user_id' => $user->id, 'emoji' => $data['emoji']]);
        }

        $msg->load(['user', 'replyTo.user', 'attachments', 'reactions']);

        return response()->json(['data' => $this->toArray($msg, $user)]);
    }

    /**
     * Закрепить/открепить сообщение (одно закреплённое на чат).
     */
    public function pin(Request $request, string $slug, int $message): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $msg = $chat->messages()->whereKey($message)->firstOrFail();

        $makePinned = ! $msg->pinned;

        DB::transaction(function () use ($chat, $msg, $makePinned) {
            $chat->messages()->where('pinned', true)->update(['pinned' => false]);
            if ($makePinned) {
                $msg->update(['pinned' => true]);
            }
        });

        return response()->json([
            'pinned' => $makePinned ? $this->quoteOf($msg->fresh(['user'])) : null,
        ]);
    }

    /**
     * Удалить сообщение: автор — своё, админ — любое.
     */
    public function destroyMessage(Request $request, string $slug, int $message): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $msg = $chat->messages()->whereKey($message)->firstOrFail();

        abort_unless($msg->user_id === $user->id || $user->isAdmin(), 403);

        foreach ($msg->attachments as $att) {
            $this->deleteFile($att->path);
        }
        $msg->delete();

        return response()->json(['status' => 'ok']);
    }

    /**
     * Участники чата (для вкладки «Участники» и упоминаний) — все, кто писал в чат.
     */
    public function participants(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        $userIds = $chat->messages()->whereNotNull('user_id')->distinct()->pluck('user_id');
        $members = User::whereIn('id', $userIds)->get()
            ->map(fn (User $u) => [
                'id' => $u->id,
                'name' => $u->name,
                'username' => $u->username,
                'avatar_url' => $u->avatar_url,
                'role' => $u->isAdmin() ? 'Админ' : 'Участник',
                'seen' => self::seenLabel($u->last_active_at),
            ])
            // Админы сверху, затем по имени.
            ->sortBy(fn ($m) => [$m['role'] === 'Админ' ? 0 : 1, $m['name']])
            ->values()
            ->all();

        return response()->json(['data' => $members]);
    }

    /**
     * Вложения чата по типу: image | file | voice | link (для вкладок инфо-экрана).
     */
    public function attachments(Request $request, string $slug, string $kind): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        if ($kind === 'link') {
            return response()->json(['data' => $this->collectLinks($chat)]);
        }

        abort_unless(in_array($kind, ['image', 'file', 'voice'], true), 404);

        $items = MessageAttachment::whereIn('message_id', $chat->messages()->select('id'))
            ->where('kind', $kind)
            ->with('message.user')
            ->orderByDesc('id')
            ->get()
            ->map(fn (MessageAttachment $a) => $this->attachmentArray($a))
            ->all();

        return response()->json(['data' => $items]);
    }

    /**
     * Отдаёт файл вложения по подписанной ссылке (без токена — для <img>/<audio>/скачивания).
     */
    public function attachmentFile(MessageAttachment $attachment): BinaryFileResponse
    {
        abort_unless(Storage::disk(self::DISK)->exists($attachment->path), 404);

        return response()->file(Storage::disk(self::DISK)->path($attachment->path));
    }

    /**
     * Фото чата (без токена — для <img>).
     */
    public function avatar(string $slug): BinaryFileResponse
    {
        $chat = Chat::where('slug', $slug)->firstOrFail();
        abort_unless($chat->avatar_path && Storage::disk(self::DISK)->exists($chat->avatar_path), 404);

        return response()->file(Storage::disk(self::DISK)->path($chat->avatar_path));
    }

    // --- Helpers ---------------------------------------------------------

    public static function resolve(string $slug, User $user): Chat
    {
        if ($slug === 'admin') {
            return self::directChatFor($user);
        }

        $chat = Chat::where('slug', $slug)->firstOrFail();

        if ($chat->type === 'direct' && $chat->user_id !== $user->id && ! $user->isAdmin()) {
            abort(404);
        }

        if ($user->leftChats()->whereKey($chat->id)->exists()) {
            abort(404);
        }

        return $chat;
    }

    /**
     * Выйти из группового чата: чат пропадает из списка пользователя и закрывается для записи.
     */
    public function leave(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        abort_if($chat->type === 'direct', 422, 'Личный чат нельзя покинуть.');

        $user->leftChats()->syncWithoutDetaching([$chat->id]);

        return response()->json(['status' => 'ok']);
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

    private function storeAttachment(Message $message, $file, string $kind, ?int $duration): MessageAttachment
    {
        $path = $file->store("chat/{$message->chat_id}", self::DISK);

        $width = null;
        $height = null;
        if ($kind === 'image') {
            $size = @getimagesize(Storage::disk(self::DISK)->path($path));
            if ($size) {
                [$width, $height] = $size;
            }
        }

        return $message->attachments()->create([
            'kind' => $kind,
            'path' => $path,
            'name' => $file->getClientOriginalName(),
            'mime' => $file->getClientMimeType(),
            'size' => $file->getSize(),
            'duration' => $kind === 'voice' ? $duration : null,
            'width' => $width,
            'height' => $height,
        ]);
    }

    private function deleteFile(?string $path): void
    {
        if ($path && Storage::disk(self::DISK)->exists($path)) {
            Storage::disk(self::DISK)->delete($path);
        }
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function collectLinks(Chat $chat): array
    {
        $links = [];
        $messages = $chat->messages()->with('user')->whereNotNull('body')->orderByDesc('id')->get();

        foreach ($messages as $m) {
            if (preg_match_all('#https?://[^\s]+#iu', (string) $m->body, $matches)) {
                foreach ($matches[0] as $url) {
                    $host = parse_url($url, PHP_URL_HOST) ?: $url;
                    $links[] = [
                        'url' => $url,
                        'title' => preg_replace('/^www\./', '', $host),
                        'text' => $m->body,
                        'author' => $m->author_name,
                        'date' => $m->created_at?->toIso8601String(),
                        'month' => $m->created_at ? self::monthLabel($m->created_at) : null,
                    ];
                }
            }
        }

        return $links;
    }

    /**
     * @return array<string, mixed>
     */
    private function attachmentArray(MessageAttachment $a): array
    {
        $m = $a->message;

        return [
            'id' => $a->id,
            'url' => $this->fileUrl($a),
            'name' => $a->name,
            'ext' => self::extOf($a->name),
            'size' => self::formatSize($a->size),
            'duration' => $a->duration,
            'author' => $m?->author_name,
            'date' => $m?->created_at?->toIso8601String(),
            'meta' => trim(self::formatSize($a->size).' / '.($m?->created_at?->translatedFormat('j M Y, H:i') ?? ''), ' /'),
        ];
    }

    private function fileUrl(MessageAttachment $a): string
    {
        return URL::temporarySignedRoute('chat.attachment', now()->addDay(), ['attachment' => $a->id]);
    }

    /**
     * @return array<string, mixed>
     */
    public static function presentChat(Chat $chat, User $viewer): array
    {
        $direct = $chat->type === 'direct';
        $ownDirect = $direct && $chat->user_id === $viewer->id;
        $last = $chat->latestMessage;

        $preview = $last?->body;
        if ($last && ($preview === null || $preview === '')) {
            $preview = 'Вложение';
        }

        return [
            'id' => $ownDirect ? 'admin' : $chat->slug,
            'type' => $chat->type,
            'title' => $ownDirect ? config('admin.display_name') : ($direct ? ($chat->owner?->name ?? $chat->title) : $chat->title),
            'subtitle' => $direct && ! $ownDirect ? $chat->owner?->username : $chat->subtitle,
            'pinned' => $chat->pinned,
            'avatar_url' => $chat->avatar_path ? "/chats/{$chat->slug}/avatar?v={$chat->updated_at?->timestamp}" : null,
            'report' => $chat->report_id,
            'sender' => $last ? ($last->user_id === $viewer->id ? 'Вы' : $last->author_name) : null,
            'preview' => $preview,
            'time' => $last?->created_at?->format('H:i'),
        ];
    }

    /**
     * Короткая цитата сообщения (для ответа и закрепа).
     *
     * @return array<string, mixed>
     */
    private function quoteOf(Message $m): array
    {
        $text = $m->body;
        if ($text === null || $text === '') {
            $att = $m->attachments->first();
            $text = match ($att?->kind) {
                'image' => 'Фото',
                'voice' => 'Голосовое сообщение',
                'file' => $att->name ?: 'Файл',
                default => 'Вложение',
            };
        }

        return [
            'id' => $m->id,
            'author' => $m->author_name,
            'text' => $text,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function toArray(Message $m, User $viewer, ?int $othersRead = null): array
    {
        $image = null;
        $images = [];
        $file = null;
        $voice = null;

        foreach ($m->attachments as $a) {
            if ($a->kind === 'image') {
                $image ??= ['url' => $this->fileUrl($a), 'width' => $a->width, 'height' => $a->height];
                $images[] = ['url' => $this->fileUrl($a), 'width' => $a->width, 'height' => $a->height];
            } elseif ($a->kind === 'voice') {
                $voice = ['url' => $this->fileUrl($a), 'duration' => self::formatDuration($a->duration)];
            } elseif ($a->kind === 'file') {
                $file = [
                    'url' => $this->fileUrl($a),
                    'name' => $a->name,
                    'ext' => self::extOf($a->name),
                    'size' => self::formatSize($a->size),
                ];
            }
        }

        // Реакции: агрегируем по эмодзи, помечаем свои.
        $reactions = $m->reactions
            ->groupBy('emoji')
            ->map(fn ($group, $emoji) => [
                'emoji' => $emoji,
                'count' => $group->count(),
                'mine' => $group->contains('user_id', $viewer->id),
            ])
            ->values()
            ->all();

        $mine = $m->user_id !== null && $m->user_id === $viewer->id;

        return [
            'id' => $m->id,
            'user_id' => $m->user_id,
            'mine' => $mine,
            'author' => $m->author_name,
            'role' => $m->author_role,
            'avatar_url' => $m->user?->avatar_url,
            'text' => $m->body,
            'time' => $m->created_at?->format('H:i'),
            'created_at' => $m->created_at?->toIso8601String(),
            'reply' => $m->replyTo ? $this->quoteOf($m->replyTo) : null,
            // image — первое фото (совместимость), images — все фото сообщения-альбома.
            'image' => $image,
            'images' => $images,
            'file' => $file,
            'voice' => $voice,
            'reactions' => $reactions,
            'pinned' => (bool) $m->pinned,
            'read' => $mine && $othersRead !== null ? $othersRead >= $m->id : false,
        ];
    }

    private static function extOf(?string $name): string
    {
        if (! $name) {
            return '';
        }
        $ext = pathinfo($name, PATHINFO_EXTENSION);

        return mb_strtolower($ext);
    }

    private static function formatSize(?int $bytes): string
    {
        if (! $bytes) {
            return '';
        }
        if ($bytes < 1024) {
            return $bytes.' б';
        }
        if ($bytes < 1024 * 1024) {
            return round($bytes / 1024, 1).' КБ';
        }

        return round($bytes / (1024 * 1024), 1).' МБ';
    }

    private static function formatDuration(?int $seconds): string
    {
        $seconds = (int) $seconds;
        $m = intdiv($seconds, 60);
        $s = $seconds % 60;

        return sprintf('%d:%02d', $m, $s);
    }

    private static function monthLabel(Carbon $date): string
    {
        return mb_convert_case($date->translatedFormat('F Y'), MB_CASE_TITLE);
    }

    public static function seenLabelFor(?Carbon $at): string
    {
        return self::seenLabel($at);
    }

    private static function seenLabel(?Carbon $at): string
    {
        if (! $at) {
            return 'не в сети';
        }
        $minutes = $at->diffInMinutes(now());
        if ($minutes < 2) {
            return 'в сети';
        }
        if ($minutes < 60) {
            return 'Был(а) в сети '.(int) $minutes.' мин назад';
        }
        $hours = $at->diffInHours(now());
        if ($hours < 24) {
            return 'Был(а) в сети '.(int) $hours.' ч назад';
        }
        $days = $at->diffInDays(now());
        if ($days < 7) {
            return 'Был(а) в сети '.(int) $days.' дн назад';
        }

        return 'Был(а) в сети '.$at->translatedFormat('j M Y');
    }
}
