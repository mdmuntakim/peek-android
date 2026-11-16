package io.github.mdmuntakim.peek.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
 * Peek "Abyss" palette -- an original color system designed for Peek:
 * deep-sea ink, signal teal, beacon amber and dusk violet. Not derived from
 * any other brand's palette.
 */
private val AbyssLight = lightColorScheme(
    primary = Color(0xFF0A6C60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4F1E7),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFFA25B08),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF371C00),
    tertiary = Color(0xFF5B4AAE),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5DEFF),
    onTertiaryContainer = Color(0xFF170463),
    background = Color(0xFFF1F6F4),
    onBackground = Color(0xFF151F1D),
    surface = Color(0xFFF9FCFA),
    onSurface = Color(0xFF151F1D),
    surfaceVariant = Color(0xFFDCE6E2),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBFC9C5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val AbyssDark = darkColorScheme(
    primary = Color(0xFF4EDCC2),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0C4A41),
    onPrimaryContainer = Color(0xFFA8F5E2),
    secondary = Color(0xFFF5B860),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF683C00),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFC6BDF8),
    onTertiary = Color(0xFF2E1A6E),
    tertiaryContainer = Color(0xFF453387),
    onTertiaryContainer = Color(0xFFE5DEFF),
    background = Color(0xFF0A1013),
    onBackground = Color(0xFFDFE8E5),
    surface = Color(0xFF0E161A),
    onSurface = Color(0xFFDFE8E5),
    surfaceVariant = Color(0xFF1C2A31),
    onSurfaceVariant = Color(0xFFA9B8B3),
    outline = Color(0xFF55605D),
    outlineVariant = Color(0xFF2A3A38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** The signature "aurora" gradient for hero surfaces and the splash. */
data class Aurora(val brush: Brush, val glow: Color)

private val AuroraLight = Aurora(
    brush = Brush.linearGradient(
        listOf(Color(0xFF0A6C60), Color(0xFF128C7E), Color(0xFF5B4AAE))
    ),
    glow = Color(0xFF128C7E),
)

private val AuroraDark = Aurora(
    brush = Brush.linearGradient(
        listOf(Color(0xFF0C4A41), Color(0xFF10695C), Color(0xFF453387))
    ),
    glow = Color(0xFF4EDCC2),
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
        colorScheme = if (dark) AbyssDark else AbyssLight,
        shapes = PeekShapes,
        content = content,
    )
}

@Composable
fun aurora(): Aurora =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) AuroraDark else AuroraLight
