<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

/**
 * Публичный профиль другого пользователя (экран «профиль юзера» в чате).
 */
class UserController extends Controller
{
    public function show(User $user): JsonResponse
    {
        return response()->json([
            'data' => [
                'id' => $user->id,
                'name' => $user->name,
                'username' => $user->username,
                'avatar_url' => $user->avatar_url,
                'registered_at' => $user->created_at?->translatedFormat('d.m.Y'),
                'registered_days' => $user->created_at ? (int) $user->created_at->diffInDays(now()) : null,
                'seen' => ChatController::seenLabelFor($user->last_active_at),
                'is_admin' => $user->isAdmin(),
            ],
        ]);
    }

    /**
     * Фото пользователя (без токена — для <img>/AsyncImage).
     */
    public function avatar(User $user): BinaryFileResponse
    {
        abort_unless($user->avatar_path && Storage::disk('local')->exists($user->avatar_path), 404);

        return response()->file(Storage::disk('local')->path($user->avatar_path));
    }
}
