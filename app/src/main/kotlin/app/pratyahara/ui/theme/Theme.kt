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

/** Accent colours shared with the overlay, which is drawn outside Compose (see res/values/colors.xml). */
object Accent {
    val Lime = Color(0xFFD4F76A)
    val OnLime = Color(0xFF141A00)
    val Violet = Color(0xFF7B61FF)
    val Coral = Color(0xFFFF7A59)
}

// Warm paper, ink-black pills and one loud lime. Calm by default, with a little bit of fun.
private val Light = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Accent.Lime,
    onPrimaryContainer = Accent.OnLime,
    secondary = Accent.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE7FF),
    onSecondaryContainer = Color(0xFF23175C),
    tertiary = Accent.Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3D8),
    onTertiaryContainer = Color(0xFF4A1405),
    background = Color(0xFFF5F4EF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFF5F4EF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE8E7E1),
    onSurfaceVariant = Color(0xFF6B6A66),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDECE6),
    surfaceContainerHighest = Color(0xFFE4E3DC),
    outline = Color(0xFFBDBCB6),
    outlineVariant = Color(0xFFDAD9D3),
    error = Color(0xFFC2410C),
)

private val Dark = darkColorScheme(
    primary = Accent.Lime,
    onPrimary = Color(0xFF111111),
    primaryContainer = Accent.Lime,
    onPrimaryContainer = Accent.OnLime,
    secondary = Color(0xFFB5A4FF),
    onSecondary = Color(0xFF1C1150),
    secondaryContainer = Color(0xFF2A2350),
    onSecondaryContainer = Color(0xFFE6E0FF),
    tertiary = Color(0xFFFF9C7F),
    onTertiary = Color(0xFF3D1205),
    tertiaryContainer = Color(0xFF3D2219),
    onTertiaryContainer = Color(0xFFFFDCCF),
    background = Color(0xFF0D0D0F),
    onBackground = Color(0xFFF3F3F0),
    surface = Color(0xFF0D0D0F),
    onSurface = Color(0xFFF3F3F0),
    surfaceVariant = Color(0xFF26262A),
    onSurfaceVariant = Color(0xFFA3A3A0),
    surfaceContainer = Color(0xFF19191C),
    surfaceContainerHigh = Color(0xFF232327),
    surfaceContainerHighest = Color(0xFF2C2C31),
    outline = Color(0xFF4A4A4F),
    outlineVariant = Color(0xFF333338),
    error = Color(0xFFFF9C7F),
)

private val Type = Typography().run {
    copy(
        displayLarge = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.Black, lineHeight = 92.sp, letterSpacing = (-3).sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Bold),
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
