<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\PanelAdmin;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Вход в веб-админку логином и паролем (сессия) и смена этих данных.
 */
class AuthController extends Controller
{
    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'login' => ['required', 'string', 'max:255'],
            'password' => ['required', 'string'],
        ]);

        if (! Auth::guard('panel')->attempt(['login' => $data['login'], 'password' => $data['password']])) {
            throw ValidationException::withMessages(['login' => 'Неверный логин или пароль.']);
        }

        $request->session()->regenerate();

        return $this->me($request);
    }

    public function logout(Request $request): JsonResponse
    {
        Auth::guard('panel')->logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return response()->json(['status' => 'ok']);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json(['login' => $request->user('panel')->login]);
    }

    /**
     * Смена логина и/или пароля админки. Текущий пароль обязателен.
     */
    public function update(Request $request): JsonResponse
    {
        /** @var PanelAdmin $admin */
        $admin = $request->user('panel');

        $data = $request->validate([
            'current_password' => ['required', 'string'],
            'login' => ['sometimes', 'required', 'string', 'max:255', Rule::unique('panel_admins', 'login')->ignore($admin->id)],
            'password' => ['sometimes', 'nullable', 'string', 'min:8', 'confirmed'],
        ], [
            'current_password.required' => 'Введите текущий пароль',
            'login.unique' => 'Такой логин уже занят',
            'password.min' => 'Пароль должен быть не короче 8 символов',
            'password.confirmed' => 'Пароли не совпадают',
        ]);

        if (! Hash::check($data['current_password'], $admin->password)) {
            throw ValidationException::withMessages(['current_password' => 'Неверный текущий пароль']);
        }

        if (isset($data['login'])) {
            $admin->login = $data['login'];
        }
        if (! empty($data['password'])) {
            $admin->password = $data['password'];
        }
        $admin->save();

        return $this->me($request);
    }
}
