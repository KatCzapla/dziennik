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
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F2EE),
    onPrimaryContainer = Color(0xFF063B37),
    secondary = Color(0xFF7C5E34),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFCFAF6),
    onBackground = Color(0xFF24211D),
    surface = Color(0xFFFCFAF6),
    onSurface = Color(0xFF24211D),
    surfaceVariant = Color(0xFFF0ECE4),
    onSurfaceVariant = Color(0xFF655F56),
    error = Color(0xFFB3261E),
    outline = Color(0xFFD8D0C5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3C7),
    onPrimary = Color(0xFF073C37),
    primaryContainer = Color(0xFF0B514A),
    onPrimaryContainer = Color(0xFFD7F2EE),
    secondary = Color(0xFFE0C391),
    onSecondary = Color(0xFF402D10),
    background = Color(0xFF171614),
    onBackground = Color(0xFFECE6DC),
    surface = Color(0xFF171614),
    onSurface = Color(0xFFECE6DC),
    surfaceVariant = Color(0xFF24211D),
    onSurfaceVariant = Color(0xFFCFC6B8),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF4E4840),
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
