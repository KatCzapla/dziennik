package pl.usagi.edziennik.ui

import androidx.compose.runtime.Composable
import pl.usagi.edziennik.ui.navigation.EDziennikNavHost
import pl.usagi.edziennik.ui.theme.EDziennikTheme

@Composable
fun EDziennikApp() {
    EDziennikTheme {
        EDziennikNavHost()
    }
}
