package pl.usagi.edziennik.util
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

internal fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrEmpty() }

internal fun String?.blankToNull(): String? =
    takeUnless { it.isNullOrBlank() }

@Composable
fun String.uppercaseLocalized(): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val polishLocale = Locale.forLanguageTag("pl-PL")
    return uppercase(polishLocale)
}