<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Данные аккаунта: смена почты, телефона, пароля (экран «Данные аккаунта»).
 */
class AccountController extends Controller
{
    public function changeEmail(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email', 'max:255', Rule::unique('users', 'email')->ignore($request->user()->id)],
        ]);

        $request->user()->update(['email' => $data['email']]);
        $this->revokeOtherTokens($request);

        return response()->json(['status' => 'ok']);
    }

    public function changePhone(Request $request): JsonResponse
    {
        $data = $request->validate([
            'phone' => ['required', 'string', 'max:32'],
        ]);

        $request->user()->update(['phone' => $data['phone']]);

        return response()->json(['status' => 'ok']);
    }

    public function changePassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'current_password' => ['nullable', 'string'],
            'password' => ['required', 'string', 'min:8', 'confirmed'],
        ]);

        $user = $request->user();

        // Если у пользователя уже есть пароль и он прислан — проверяем его.
        if (! empty($data['current_password']) && ! Hash::check($data['current_password'], (string) $user->password)) {
            throw ValidationException::withMessages([
                'current_password' => 'Текущий пароль неверный.',
            ]);
        }

        $user->update(['password' => $data['password']]);

        // Остальные устройства выходят: украденный токен не переживёт смену пароля.
        $this->revokeOtherTokens($request);

        return response()->json(['status' => 'ok']);
    }

    /** Удаляет все токены пользователя, кроме текущего. */
    private function revokeOtherTokens(Request $request): void
    {
        $current = $request->user()->currentAccessToken();
        $request->user()->tokens()
            ->when($current && isset($current->id), fn ($q) => $q->whereKeyNot($current->id))
            ->delete();
    }
}
