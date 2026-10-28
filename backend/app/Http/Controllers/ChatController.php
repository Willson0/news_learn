<?php

namespace App\Http\Controllers;

use App\Models\Chat;
use App\Models\ChatRead;
use App\Models\Message;
use App\Models\MessageAttachment;
use App\Models\User;
use App\Services\PushService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\URL;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

class ChatController extends Controller
{
    private const DISK = 'local';

    /** Может ли зритель удалять чужие сообщения у всех (админ, создатель отчёта). */
    private bool $moderator = false;

    /** Зритель видит все личные ветки «/текст/» (админ, создатель отчёта). */
    private bool $staff = false;

    /** Чат отчёта: своя личная ветка подписывается «Автору», иначе «Администраторам». */
    private bool $reportChat = false;

    public function __construct(private PushService $push) {}

    /** Права зрителя в этом чате — нужны при сборке сообщений. */
    private function context(Chat $chat, User $user): void
    {
        $this->moderator = $user->isAdmin() || $chat->canBeManagedBy($user);
        $this->staff = $chat->isStaff($user);
        $this->reportChat = $chat->type === 'report';
    }

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

        // Без подписки доступны только общие чаты и чат с админом; чаты отчётов — по подписке.
        $sectionsAllowed = $user->hasAccess() ? ['main', 'reports'] : ['main'];

        $chats = Chat::with('latestMessage')
            ->whereIn('section', $sectionsAllowed)
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
        $this->context($chat, $user);
        $this->ensureJoined($chat, $user);

        $messages = $chat->messages()
            ->visibleTo($user, $this->staff)
            ->with(['user', 'privateOwner', 'replyTo.user', 'replyTo.attachments', 'attachments', 'reactions'])
            ->oldest('id')
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

        $pins = $this->pinsOf($chat, $user);

        return response()->json([
            'chat' => self::presentChat($chat, $user),
            // pinned — последнее закреплённое (как в шапке Телеграма), pins — все по порядку закрепления.
            'pinned' => $pins === [] ? null : end($pins),
            'pins' => $pins,
            'data' => $payload,
        ]);
    }

    /**
     * Отметить чат прочитанным без открытия (меню чата в списке, как в Телеграме).
     */
    public function markRead(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $this->context($chat, $user);

        $lastId = (int) $chat->messages()->visibleTo($user, $this->staff)->max('id');
        if ($lastId > 0) {
            ChatRead::updateOrCreate(
                ['chat_id' => $chat->id, 'user_id' => $user->id],
                ['last_read_message_id' => $lastId],
            );
        }

        return response()->json(['status' => 'ok']);
    }

    /**
     * Отправить сообщение: текст и/или вложение (image|file|voice), опционально ответ.
     */
    public function send(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $this->context($chat, $user);

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

        // Тип файла проверяем по содержимому: «фото» должно быть картинкой, «голосовое» — аудио.
        $kindRules = [
            'image' => ['mimes:jpg,jpeg,png,gif,webp,heic,heif'],
            'voice' => ['mimetypes:audio/mp4,audio/x-m4a,audio/m4a,audio/aac,audio/mpeg,audio/ogg,audio/opus,audio/wav,audio/x-wav,audio/webm,video/mp4,video/webm,application/ogg'],
        ];
        if (isset($data['kind'], $kindRules[$data['kind']])) {
            $request->validate([
                'attachment' => $kindRules[$data['kind']],
                'attachments.*' => $kindRules[$data['kind']],
            ], [
                'attachment.mimes' => 'Можно отправить только фото.',
                'attachments.*.mimes' => 'Можно отправить только фото.',
                'attachment.mimetypes' => 'Неподдерживаемый формат голосового сообщения.',
            ]);
        }

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

        $body = $hasBody ? trim($data['body']) : null;

        // Ответ допустим только на видимое пользователю обычное сообщение этого же чата.
        $replyTo = null;
        if (! empty($data['reply_to'])) {
            $replyTo = $chat->messages()->visibleTo($user, $this->staff)->whereKey($data['reply_to'])->first();
            if ($replyTo?->isSystem()) {
                $replyTo = null;
            }
        }

        // Личная ветка: «/текст/» участника видят только он и staff чата (админы, создатель
        // отчёта). Ответ на сообщение ветки на любой глубине остаётся в ней же.
        $privateOwner = null;
        if ($replyTo?->isPrivate()) {
            $privateOwner = $replyTo->private_owner_id;
        } elseif ($body !== null && $chat->type !== 'direct' && ! $this->staff
            && preg_match('#^/(.*\S.*)/$#su', $body, $m)) {
            $privateOwner = $user->id;
            $body = trim($m[1]);
        }

        $this->ensureJoined($chat, $user);

        $message = DB::transaction(function () use ($chat, $user, $data, $body, $replyTo, $privateOwner, $files) {
            $message = $chat->messages()->create([
                'user_id' => $user->id,
                'reply_to_id' => $replyTo?->id,
                'private_owner_id' => $privateOwner,
                'author_name' => $user->name,
                'author_role' => $user->isAdmin() ? 'Админ' : 'Участник',
                'body' => $body,
            ]);

            foreach ($files as $file) {
                $this->storeAttachment($message, $file, $data['kind'], $data['duration'] ?? null);
            }

            return $message;
        });

        $message->load(['user', 'privateOwner', 'replyTo.user', 'replyTo.attachments', 'attachments', 'reactions']);

        $this->notifyRecipients($chat, $user, $message);

        return response()->json(['data' => $this->toArray($message, $user)], 201);
    }

    /**
     * Поставить/снять реакцию на сообщение.
     */
    public function react(Request $request, string $slug, int $message): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $this->context($chat, $user);
        $msg = $chat->messages()->visibleTo($user, $this->staff)->whereKey($message)->firstOrFail();
        abort_if($msg->isSystem(), 422, 'На служебное сообщение нельзя поставить реакцию.');

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

        $msg->load(['user', 'privateOwner', 'replyTo.user', 'replyTo.attachments', 'attachments', 'reactions']);

        return response()->json(['data' => $this->toArray($msg, $user)]);
    }

    /**
     * Закрепить/открепить сообщение. Закрепов может быть несколько (как в Телеграме);
     * закреплять и откреплять могут только админы и создатель отчёта.
     */
    public function pin(Request $request, string $slug, int $message): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $this->context($chat, $user);
        abort_unless($chat->canPinBy($user), 403, 'Закреплять сообщения могут только администратор и автор отчёта.');

        $msg = $chat->messages()->visibleTo($user, $this->staff)->whereKey($message)->firstOrFail();
        abort_if($msg->isSystem() || $msg->isPrivate(), 422, 'Это сообщение нельзя закрепить.');

        $makePinned = ! $msg->pinned;
        $msg->update(['pinned' => $makePinned, 'pinned_at' => $makePinned ? now() : null]);

        $pins = $this->pinsOf($chat, $user);

        return response()->json([
            'pinned' => $pins === [] ? null : end($pins),
            'pins' => $pins,
        ]);
    }

    /**
     * Закреплённые сообщения чата в порядке закрепления (старые → новые).
     *
     * @return array<int, array<string, mixed>>
     */
    private function pinsOf(Chat $chat, User $user): array
    {
        return $chat->messages()
            ->visibleTo($user, $this->staff)
            ->where('pinned', true)
            ->whereNull('private_owner_id')
            ->with(['user', 'attachments'])
            ->orderBy('pinned_at')
            ->orderBy('id')
            ->get()
            ->map(fn (Message $m) => $this->quoteOf($m))
            ->all();
    }

    /**
     * Удалить сообщение. for=me — скрыть только у себя (на всех своих устройствах);
     * for=all (по умолчанию) — у всех: автор — своё, админ и создатель отчёта — любое.
     */
    public function destroyMessage(Request $request, string $slug, int $message): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);
        $this->context($chat, $user);
        $msg = $chat->messages()->visibleTo($user, $this->staff)->whereKey($message)->firstOrFail();

        if ($request->input('for', $request->query('for')) === 'me') {
            DB::table('message_hides')->insertOrIgnore([
                'user_id' => $user->id,
                'message_id' => $msg->id,
                'created_at' => now(),
                'updated_at' => now(),
            ]);

            return response()->json(['status' => 'ok']);
        }

        abort_unless($this->canDeleteForAll($msg, $user), 403);

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
        $this->context($chat, $user);

        $userIds = $chat->messages()->visibleTo($user, $this->staff)->whereNotNull('user_id')->distinct()->pluck('user_id');
        $leftIds = DB::table('chat_leaves')->where('chat_id', $chat->id)->pluck('user_id');
        $members = User::whereIn('id', $userIds)->whereNotIn('id', $leftIds)->get()
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
        $this->context($chat, $user);

        if ($kind === 'link') {
            return response()->json(['data' => $this->collectLinks($chat, $user)]);
        }

        abort_unless(in_array($kind, ['image', 'file', 'voice'], true), 404);

        $items = MessageAttachment::whereIn('message_id', $chat->messages()->visibleTo($user, $this->staff)->select('id'))
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

        $path = Storage::disk(self::DISK)->path($attachment->path);
        $headers = [
            // Вложение не меняется: кэшируем в браузере/приложении, чтобы фото не грузились заново.
            'Cache-Control' => 'private, max-age=2592000, immutable',
            // Пользовательский файл не исполняется в браузере на нашем домене (защита от XSS через html/svg).
            'Content-Security-Policy' => "default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'; sandbox",
            'X-Content-Type-Options' => 'nosniff',
        ];

        // Картинки и аудио отдаём для просмотра, всё остальное — только как скачивание.
        $mime = (string) (mime_content_type($path) ?: 'application/octet-stream');
        $inline = in_array($mime, ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/heif'], true)
            || str_starts_with($mime, 'audio/') || in_array($mime, ['video/mp4', 'video/webm', 'application/ogg'], true);
        if (! $inline) {
            return response()->download($path, $attachment->name ?: basename($path), $headers + [
                'Content-Type' => 'application/octet-stream',
            ]);
        }

        return response()->file($path, $headers + ['Content-Type' => $mime]);
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

    /**
     * Кого можно упомянуть через «@»: все, у кого есть доступ к чату (кроме себя),
     * с тегом; q — поиск по имени и тегу.
     */
    public function members(Request $request, string $slug): JsonResponse
    {
        $user = $request->user();
        $chat = self::resolve($slug, $user);

        $q = trim((string) $request->query('q', ''));
        $query = User::query()
            ->whereKeyNot($user->id)
            ->whereNotNull('username')
            ->where('username', '!=', '');
        if ($q !== '') {
            $like = '%'.addcslashes(ltrim($q, '@'), '%_\\').'%';
            $query->where(fn ($w) => $w->where('name', 'like', $like)->orWhere('username', 'like', $like));
        }

        $members = $query->orderBy('name')->limit(200)->get()
            ->filter(fn (User $u) => self::canAccess($chat, $u))
            ->take(30)
            ->map(fn (User $u) => [
                'id' => $u->id,
                'name' => $u->name,
                'username' => self::handle($u->username),
                'avatar_url' => $u->avatar_url,
                'role' => $u->isAdmin() ? 'Админ' : 'Участник',
                'seen' => self::seenLabel($u->last_active_at),
            ])
            ->values()
            ->all();

        return response()->json(['data' => $members]);
    }

    // --- Helpers ---------------------------------------------------------

    /** Есть ли у пользователя доступ к чату (тот же смысл, что и в resolve()). */
    public static function canAccess(Chat $chat, User $user): bool
    {
        if ($chat->type === 'direct') {
            return $chat->user_id === $user->id || $user->isAdmin();
        }
        if ($chat->section !== 'main' && ! $user->hasAccess()) {
            return false;
        }

        return ! $user->leftChats()->whereKey($chat->id)->exists();
    }

    /** Тег всегда с «@» в начале. */
    private static function handle(?string $username): ?string
    {
        if ($username === null || $username === '') {
            return null;
        }

        return str_starts_with($username, '@') ? $username : '@'.$username;
    }

    /**
     * Первый вход в групповой чат — служебное сообщение «зашёл в чат». Кто уже открывал чат
     * или писал в нём до появления этих сообщений, считается вошедшим.
     */
    private function ensureJoined(Chat $chat, User $user): void
    {
        if ($chat->type === 'direct') {
            return;
        }

        $seen = ChatRead::where('chat_id', $chat->id)->where('user_id', $user->id)->exists()
            || $chat->messages()->where('user_id', $user->id)->exists();
        if ($seen) {
            return;
        }

        $chat->messages()->create([
            'user_id' => $user->id,
            'author_name' => $user->name,
            'author_role' => $user->isAdmin() ? 'Админ' : 'Участник',
            'event' => 'join',
        ]);
    }

    private function canDeleteForAll(Message $m, User $user): bool
    {
        return $this->moderator || ($m->user_id === $user->id && ! $m->isSystem());
    }

    /**
     * Push-уведомления: адресату личного сообщения и упомянутым через «@тег».
     */
    private function notifyRecipients(Chat $chat, User $sender, Message $message): void
    {
        $text = $message->body ?? $this->quoteOf($message)['text'];

        try {
            if ($message->isPrivate()) {
                $this->notifyPrivate($chat, $sender, $message, $text);

                return;
            }

            if ($message->body === null || ! preg_match_all('/@([\p{L}\p{N}_.]+)/u', $message->body, $mm)) {
                return;
            }

            $tags = collect($mm[1])->map(fn ($t) => rtrim($t, '.'))->filter()->unique()->take(50);
            $handles = $tags->flatMap(fn ($t) => ['@'.$t, $t])->all();

            User::whereIn('username', $handles)
                ->whereKeyNot($sender->id)
                ->get()
                ->filter(fn (User $u) => self::canAccess($chat, $u))
                ->each(function (User $u) use ($chat, $sender, $text, $message) {
                    $slug = $chat->type === 'direct' && $chat->user_id === $u->id ? 'admin' : $chat->slug;
                    $this->push->sendToUser($u, "Вас упомянули · {$chat->title}", "{$sender->name}: {$text}", [
                        'type' => 'chat',
                        'chat' => $slug,
                        'message' => (string) $message->id,
                    ]);
                });
        } catch (\Throwable $e) {
            // Сбой push не должен мешать отправке сообщения.
            Log::warning('Push о сообщении не отправлен', ['message' => $message->id, 'error' => $e->getMessage()]);
        }
    }

    /**
     * Push по личной ветке: сообщение участника — всем staff чата (админам, создателю
     * отчёта), ответ staff — владельцу ветки.
     */
    private function notifyPrivate(Chat $chat, User $sender, Message $message, string $text): void
    {
        $data = ['type' => 'chat', 'chat' => $chat->slug, 'message' => (string) $message->id];
        $owner = $message->privateOwner;

        if ($owner && $owner->id !== $sender->id) {
            $this->push->sendToUser($owner, "Личный ответ · {$chat->title}", "{$sender->name}: {$text}", $data);

            return;
        }

        $title = ($chat->type === 'report' ? 'Сообщение автору' : 'Сообщение администраторам')." · {$chat->title}";
        User::whereKeyNot($sender->id)->get()
            ->filter(fn (User $u) => $chat->isStaff($u) && self::canAccess($chat, $u))
            ->each(fn (User $u) => $this->push->sendToUser($u, $title, "{$sender->name}: {$text}", $data));
    }

    /** Текст служебного сообщения для превью и цитат. */
    private static function eventText(Message $m): string
    {
        $name = $m->author_name ?: 'Пользователь';

        return $m->event === 'leave' ? "{$name} вышел из чата" : "{$name} зашел в чат";
    }

    public static function resolve(string $slug, User $user): Chat
    {
        if ($slug === 'admin') {
            return self::directChatFor($user);
        }

        $chat = Chat::where('slug', $slug)->firstOrFail();

        if ($chat->type === 'direct' && $chat->user_id !== $user->id && ! $user->isAdmin()) {
            abort(404);
        }

        if ($chat->type !== 'direct' && $chat->section !== 'main' && ! $user->hasAccess()) {
            abort(403, 'Чат доступен по подписке.');
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

        abort_if($chat->isPermanent(), 422, $chat->section === 'main'
            ? 'Из общего чата нельзя выйти.'
            : 'Этот чат нельзя покинуть: он удаляется вместе с отчётом.');

        DB::transaction(function () use ($chat, $user) {
            $chat->messages()->create([
                'user_id' => $user->id,
                'author_name' => $user->name,
                'author_role' => $user->isAdmin() ? 'Админ' : 'Участник',
                'event' => 'leave',
            ]);
            $user->leftChats()->syncWithoutDetaching([$chat->id]);
        });

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
    private function collectLinks(Chat $chat, User $user): array
    {
        $links = [];
        $messages = $chat->messages()->visibleTo($user, $this->staff)->with('user')->whereNotNull('body')->orderByDesc('id')->get();

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
        // Срок ссылки — конец следующего месяца: адрес не меняется при каждом открытии чата
        // (кэш картинок работает), а старые вложения не перестают открываться через 30 дней.
        $expires = now()->startOfMonth()->addMonths(2);

        return URL::temporarySignedRoute('chat.attachment', $expires, ['attachment' => $a->id]);
    }

    /**
     * @return array<string, mixed>
     */
    public static function presentChat(Chat $chat, User $viewer): array
    {
        $direct = $chat->type === 'direct';
        $ownDirect = $direct && $chat->user_id === $viewer->id;
        $last = $chat->latestMessage;
        $staff = $chat->isStaff($viewer);

        // Последнее сообщение может быть чужим личным или скрытым у зрителя — тогда берём видимое.
        if ($last && (! $last->isVisibleTo($viewer, $staff)
            || DB::table('message_hides')->where('user_id', $viewer->id)->where('message_id', $last->id)->exists())) {
            $last = $chat->messages()->visibleTo($viewer, $staff)->latest('id')->first();
        }

        $preview = $last?->body;
        if ($last?->isSystem()) {
            $preview = self::eventText($last);
        } elseif ($last && ($preview === null || $preview === '')) {
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
            'can_manage' => $chat->canBeManagedBy($viewer),
            'can_pin' => $chat->canPinBy($viewer),
            // «/текст/» — личное сообщение администрации (в чате отчёта — и автору отчёта).
            'author_messages' => ! $direct && ! $staff,
            'permanent' => $chat->isPermanent(),
            'sender' => $last && ! $last->isSystem() ? ($last->user_id === $viewer->id ? 'Вы' : $last->author_name) : null,
            'preview' => $preview,
            'time' => $last?->created_at?->format('H:i'),
            'unread' => self::unreadCount($chat, $viewer, $staff),
        ];
    }

    /**
     * Непрочитанные: чужие видимые сообщения после последнего прочитанного. Чат ещё не
     * открывался — считаем только то, что пришло после регистрации пользователя.
     */
    private static function unreadCount(Chat $chat, User $viewer, bool $staff): int
    {
        $query = $chat->messages()->visibleTo($viewer, $staff)
            ->whereNull('event')
            ->where(fn ($q) => $q->whereNull('user_id')->orWhere('user_id', '!=', $viewer->id));

        $lastRead = ChatRead::where('chat_id', $chat->id)->where('user_id', $viewer->id)->value('last_read_message_id');
        if ($lastRead !== null) {
            $query->where('id', '>', $lastRead);
        } elseif ($viewer->created_at) {
            $query->where('created_at', '>=', $viewer->created_at);
        }

        return $query->count();
    }

    /**
     * Короткая цитата сообщения (для ответа и закрепа).
     *
     * @return array<string, mixed>
     */
    private function quoteOf(Message $m): array
    {
        $text = $m->body;
        if ($m->isSystem()) {
            $text = self::eventText($m);
        } elseif ($text === null || $text === '') {
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
            // Служебное сообщение: join — «зашел в чат», leave — «вышел из чата» (имя — author, профиль — user_id).
            'event' => $m->event,
            // Личная ветка «/текст/»: видят её владелец и staff чата.
            'private' => $m->isPrivate(),
            'private_label' => $m->isPrivate() ? $this->privateLabel($m, $viewer, $mine) : null,
            'can_delete' => $this->canDeleteForAll($m, $viewer),
        ];
    }

    /**
     * Подпись личного сообщения. Владелец ветки: своё — «Автору» (чат отчёта) или
     * «Администраторам», ответ ему — «Лично вам». Staff — «Лично · имя владельца».
     */
    private function privateLabel(Message $m, User $viewer, bool $mine): string
    {
        if ($m->private_owner_id === $viewer->id && ! $this->staff) {
            if (! $mine) {
                return 'Лично вам';
            }

            return $this->reportChat ? 'Автору' : 'Администраторам';
        }

        return 'Лично · '.($m->privateOwner?->name ?? 'участник');
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
