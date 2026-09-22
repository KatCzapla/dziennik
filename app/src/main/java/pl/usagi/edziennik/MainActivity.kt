package pl.usagi.edziennik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pl.usagi.edziennik.ui.EDziennikApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_EDziennik)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EDziennikApp()
        }
    }
}
