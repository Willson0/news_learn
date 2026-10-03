<?php

namespace App\Http\Controllers;

use App\Models\Instrument;
use Illuminate\Http\JsonResponse;

class InstrumentController extends Controller
{
    public function index(): JsonResponse
    {
        $instruments = Instrument::orderBy('position')->get()
            ->map(fn (Instrument $i) => [
                'key' => $i->key,
                'label' => $i->label,
                'category' => $i->category,
            ]);

        return response()->json(['data' => $instruments]);
    }
}
