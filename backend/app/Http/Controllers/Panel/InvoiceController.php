<?php

namespace App\Http\Controllers\Panel;

use App\Http\Controllers\Controller;
use App\Models\Invoice;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class InvoiceController extends Controller
{
    /** Все выставленные счета с фильтром по статусу. */
    public function index(Request $request): JsonResponse
    {
        $query = Invoice::with('user')->latest();

        if ($status = $request->query('status')) {
            $query->where('status', $status);
        }

        $invoices = $query->paginate(25);

        return response()->json([
            'data' => $invoices->getCollection()->map(fn (Invoice $i) => [
                'id' => $i->id,
                'user' => ['id' => $i->user_id, 'name' => $i->user?->name],
                'amount' => $i->amount,
                'months' => $i->months,
                'status' => $i->status,
                'created_at' => $i->created_at?->format('d.m.Y H:i'),
                'paid_at' => $i->paid_at?->format('d.m.Y H:i'),
                'created_by' => $i->created_by,
            ])->values(),
            'meta' => ['total' => $invoices->total(), 'page' => $invoices->currentPage(), 'last_page' => $invoices->lastPage()],
        ]);
    }
}
