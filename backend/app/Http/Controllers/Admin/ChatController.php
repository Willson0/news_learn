<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\ChatController as PublicChatController;
use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;

/**
 * Настройки чата админом: название и фотография.
 */
class ChatController extends Controller
{
    public function update(Request $request, string $slug): JsonResponse
    {
        $chat = PublicChatController::resolve($slug, $request->user());

        $data = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'avatar' => ['nullable', 'image', 'max:8192'],
        ], [
            'title.required' => 'Напишите название',
            'avatar.image' => 'Нужна фотография',
        ]);

        $chat->title = $data['title'];
        if ($request->hasFile('avatar')) {
            if ($chat->avatar_path) {
                Storage::disk('local')->delete($chat->avatar_path);
            }
            $chat->avatar_path = $request->file('avatar')->store('chats/avatars', 'local');
        }
        $chat->save();

        return response()->json(['data' => PublicChatController::presentChat($chat, $request->user())]);
    }
}
