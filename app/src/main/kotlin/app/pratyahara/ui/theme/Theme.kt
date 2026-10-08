package app.pratyahara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Soft sage greens and sky blues. No reds anywhere: even "locked" is calm.
private val Light = lightColorScheme(
    primary = Color(0xFF2F7D6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF0B3A30),
    secondary = Color(0xFF4A7A9B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBF5),
    onSecondaryContainer = Color(0xFF1C3B50),
    tertiary = Color(0xFF7A8F4E),
    tertiaryContainer = Color(0xFFE6EFD2),
    onTertiaryContainer = Color(0xFF2B3812),
    background = Color(0xFFF4F8F5),
    onBackground = Color(0xFF1B2B26),
    surface = Color(0xFFF4F8F5),
    onSurface = Color(0xFF1B2B26),
    surfaceVariant = Color(0xFFE3EDE8),
    onSurfaceVariant = Color(0xFF4E615B),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF4F0),
    outline = Color(0xFF8FA39C),
    error = Color(0xFF9A6B2F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD3BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF1E5245),
    onPrimaryContainer = Color(0xFFCDEBE1),
    secondary = Color(0xFFA7CBE6),
    onSecondary = Color(0xFF0E3349),
    secondaryContainer = Color(0xFF24394A),
    onSecondaryContainer = Color(0xFFCFE5F5),
    tertiary = Color(0xFFC3D49B),
    tertiaryContainer = Color(0xFF3A4A22),
    onTertiaryContainer = Color(0xFFE6EFD2),
    background = Color(0xFF101917),
    onBackground = Color(0xFFE1ECE8),
    surface = Color(0xFF101917),
    onSurface = Color(0xFFE1ECE8),
    surfaceVariant = Color(0xFF2A3833),
    onSurfaceVariant = Color(0xFFA9BCB5),
    surfaceContainer = Color(0xFF1A2522),
    surfaceContainerHigh = Color(0xFF22302C),
    outline = Color(0xFF6E827B),
    error = Color(0xFFE2B77E),
)

private val Type = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        displayLarge = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Light, lineHeight = 80.sp),
    )
}

@Composable
fun PratyaharaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        content = content,
    )
}
