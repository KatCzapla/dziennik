package pl.katcz.edziennik.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7285),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5F2F5),
    onPrimaryContainer = Color(0xFF06343D),
    secondary = Color(0xFFC26A15),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFAF6),
    onBackground = Color(0xFF243039),
    surface = Color(0xFFFBFAF6),
    onSurface = Color(0xFF243039),
    surfaceVariant = Color(0xFFEAF1F1),
    onSurfaceVariant = Color(0xFF5E6870),
    error = Color(0xFFB3261E),
    outline = Color(0xFFC8D5D7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF75D4E2),
    onPrimary = Color(0xFF04343D),
    primaryContainer = Color(0xFF064E5C),
    onPrimaryContainer = Color(0xFFD5F2F5),
    secondary = Color(0xFFFFB86A),
    onSecondary = Color(0xFF462400),
    background = Color(0xFF12181C),
    onBackground = Color(0xFFE7EEF0),
    surface = Color(0xFF12181C),
    onSurface = Color(0xFFE7EEF0),
    surfaceVariant = Color(0xFF223035),
    onSurfaceVariant = Color(0xFFC1CDD1),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF44585F),
)

private val EDziennikTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val EDziennikShapes = Shapes()

@Immutable
data class AppSpacing(
    val xxs: androidx.compose.ui.unit.Dp = 4.dp,
    val xs: androidx.compose.ui.unit.Dp = 8.dp,
    val sm: androidx.compose.ui.unit.Dp = 12.dp,
    val md: androidx.compose.ui.unit.Dp = 16.dp,
    val lg: androidx.compose.ui.unit.Dp = 24.dp,
    val xl: androidx.compose.ui.unit.Dp = 32.dp,
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }

object EDziennikTheme {
    val spacing: AppSpacing
        @Composable
        get() = LocalAppSpacing.current
}

@Composable
fun EDziennikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppSpacing provides AppSpacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EDziennikTypography,
            shapes = EDziennikShapes,
            content = content,
        )
    }
}
