package app.karta.likvidnosti

import android.app.Application
import app.karta.likvidnosti.data.TokenStore

class KartaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
    }
}
