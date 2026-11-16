package io.github.mdmuntakim.peek.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted appearance preference. */
object ThemeStore {
    private const val PREFS = "peek_prefs"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return name?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}

/**
 * Peek "Lagoon" palette -- an original color system designed for Peek:
 * deep lagoon teal, burnt amber and plum accents. Not derived from any
 * other brand's palette.
 */
private val LagoonLight = lightColorScheme(
    primary = Color(0xFF0E7268),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F5EC),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFFB25C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF381C00),
    tertiary = Color(0xFF7B4B8E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6D9FF),
    onTertiaryContainer = Color(0xFF2E0A3C),
    background = Color(0xFFF5FAF8),
    onBackground = Color(0xFF16201D),
    surface = Color(0xFFF5FAF8),
    onSurface = Color(0xFF16201D),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF404946),
    outline = Color(0xFF707976),
    outlineVariant = Color(0xFFBFC9C5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val LagoonDark = darkColorScheme(
    primary = Color(0xFF61D9C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF82F5DE),
    secondary = Color(0xFFFFB874),
    onSecondary = Color(0xFF4A2400),
    secondaryContainer = Color(0xFF693900),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFE2B7EF),
    onTertiary = Color(0xFF441751),
    tertiaryContainer = Color(0xFF5C3069),
    onTertiaryContainer = Color(0xFFF6D9FF),
    background = Color(0xFF0C1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0C1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Soft, rounded shape system for a calm professional feel. */
private val PeekShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PeekTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) LagoonDark else LagoonLight,
        shapes = PeekShapes,
        content = content,
    )
}
