<?php

namespace App\Http\Controllers;

use App\Models\AppSetting;
use Illuminate\Http\JsonResponse;

/**
 * Документы (пользовательское соглашение и т. п.): приложение открывает их ссылками внутри себя.
 */
class DocumentController extends Controller
{
    public function index(): JsonResponse
    {
        return response()->json(['data' => AppSetting::get('documents', [])]);
    }
}
