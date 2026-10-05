package app.karta.likvidnosti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.karta.likvidnosti.ui.AppRoot
import app.karta.likvidnosti.ui.theme.KartaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KartaTheme {
                AppRoot()
            }
        }
    }
}
