package app.melosome.claimconnect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 tonal-spot scheme generated from the ClaimConnect app icon's true brand teal
// (#49969D, sampled from the icon artwork), via the standard HCT tonal-palette algorithm
// (material-color-utilities, 2021 spec). Regenerate both schemes together if the brand seed
// colour ever changes, rather than hand-editing individual roles.
private val light = lightColorScheme(
    primary = Color(0xFF006970), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF0F8), onPrimaryContainer = Color(0xFF004F54),
    secondary = Color(0xFF4A6365), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EA), onSecondaryContainer = Color(0xFF324B4D),
    tertiary = Color(0xFF4F5E7D), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E2FF), onTertiaryContainer = Color(0xFF384764),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FAFB), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FAFB), onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E5), onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A), outlineVariant = Color(0xFFBEC8C9),
)
private val dark = darkColorScheme(
    primary = Color(0xFF80D4DC), onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54), onPrimaryContainer = Color(0xFF9DF0F8),
    secondary = Color(0xFFB1CBCE), onSecondary = Color(0xFF1B3437),
    secondaryContainer = Color(0xFF324B4D), onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFB7C7EA), onTertiary = Color(0xFF21304C),
    tertiaryContainer = Color(0xFF384764), onTertiaryContainer = Color(0xFFD7E2FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415), onBackground = Color(0xFFDEE4E4),
    surface = Color(0xFF0E1415), onSurface = Color(0xFFDEE4E4),
    surfaceVariant = Color(0xFF3F4849), onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899393), outlineVariant = Color(0xFF3F4849),
)

@Composable fun ClaimConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, typography = Typography(), content = content)
}
