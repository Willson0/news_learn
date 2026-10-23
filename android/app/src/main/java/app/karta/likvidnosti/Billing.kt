package app.karta.likvidnosti

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

/** Возврат из YooKassa после привязки карты: karta://billing?status=… */
object BillingReturn {
    val results = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
}
