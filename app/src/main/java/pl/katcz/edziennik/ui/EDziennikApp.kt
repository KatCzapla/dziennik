package pl.katcz.edziennik.ui

import androidx.compose.runtime.Composable
import pl.katcz.edziennik.ui.navigation.EDziennikNavHost
import pl.katcz.edziennik.ui.theme.EDziennikTheme

@Composable
fun EDziennikApp() {
    EDziennikTheme {
        EDziennikNavHost()
    }
}
