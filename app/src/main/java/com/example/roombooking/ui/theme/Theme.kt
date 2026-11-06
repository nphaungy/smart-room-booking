package com.example.roombooking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roombooking.R

/**
 * Deep emerald with champagne gold on ivory. These colours stay the same in light and dark mode,
 * because the top bar and hero cards are emerald in both; anything on the page uses the colour scheme.
 */
object Brand {
    val Ink = Color(0xFF0B3D2E)

    /** Champagne for small labels and accents on top of the emerald areas. */
    val Highlight = Color(0xFFE6CF97)

    /** Emerald with a soft sheen across the middle. */
    val Hero = listOf(Color(0xFF0B3D2E), Color(0xFF14573F), Color(0xFF0B3D2E))

    /** Light enough to stay visible on the dark page, and still carries white text. */
    val Emerald = listOf(Color(0xFF0F4C3A), Color(0xFF1B6B52))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F4C3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EDE6),
    onPrimaryContainer = Color(0xFF0B3D2E),
    inversePrimary = Color(0xFFD9BE7C),
    secondary = Color(0xFF8A6D2F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E9D2),
    onSecondaryContainer = Color(0xFF4A3A12),
    tertiary = Color(0xFF6B5B3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E9D2),
    onTertiaryContainer = Color(0xFF4A3A12),
    background = Color(0xFFFBF8F1),
    onBackground = Color(0xFF1F2A25),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF1F2A25),
    surfaceVariant = Color(0xFFEFE9DC),
    onSurfaceVariant = Color(0xFF5A5F54),
    surfaceTint = Color(0xFF0F4C3A),
    inverseSurface = Color(0xFF1F2A25),
    inverseOnSurface = Color(0xFFF7F3E8),
    error = Color(0xFF9B1C1C),
    onError = Color.White,
    errorContainer = Color(0xFFF7E4E4),
    onErrorContainer = Color(0xFF5F1111),
    outline = Color(0xFF85806F), // at least 3:1 on every light surface, so the edge of a field can be seen
    outlineVariant = Color(0xFFE4DECF),
    surfaceBright = Color(0xFFFBF8F1),
    surfaceDim = Color(0xFFE9E3D5),
    // Each level is a step deeper into the ivory, so depth comes from tone rather than shadow.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFBF6),
    surfaceContainer = Color(0xFFF7F3EA),
    surfaceContainerHigh = Color(0xFFF2EDE2),
    surfaceContainerHighest = Color(0xFFECE6D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9BE7C),
    onPrimary = Color(0xFF2A2008),
    primaryContainer = Color(0xFF1F4A3A),
    onPrimaryContainer = Color(0xFFF3E9D2),
    inversePrimary = Color(0xFF0F4C3A),
    secondary = Color(0xFFD9BE7C),
    onSecondary = Color(0xFF2A2008),
    secondaryContainer = Color(0xFF4A3B16),
    onSecondaryContainer = Color(0xFFF3E9D2),
    tertiary = Color(0xFFC9B98F),
    onTertiary = Color(0xFF2A2008),
    tertiaryContainer = Color(0xFF4A3B16),
    onTertiaryContainer = Color(0xFFF3E9D2),
    background = Color(0xFF0B1F19),
    onBackground = Color(0xFFEFE9DC),
    surface = Color(0xFF0B1F19),
    onSurface = Color(0xFFEFE9DC),
    surfaceVariant = Color(0xFF1D3A30),
    onSurfaceVariant = Color(0xFFB8B5A5),
    surfaceTint = Color(0xFFD9BE7C),
    inverseSurface = Color(0xFFEFE9DC),
    inverseOnSurface = Color(0xFF1F2A25),
    error = Color(0xFFE8A0A0),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFF5D5D5),
    outline = Color(0xFF8A8F80),
    outlineVariant = Color(0xFF2B463B),
    surfaceBright = Color(0xFF24443A),
    surfaceDim = Color(0xFF0B1F19),
    surfaceContainerLowest = Color(0xFF11291F),
    surfaceContainerLow = Color(0xFF132C23),
    surfaceContainer = Color(0xFF153027),
    surfaceContainerHigh = Color(0xFF183429),
    surfaceContainerHighest = Color(0xFF1B382D),
)

/** Headings: lighter weights read as more refined than bold. Bundled so it looks the same on every device. */
private val Prompt = FontFamily(
    Font(R.font.prompt_medium, FontWeight.Medium),
    Font(R.font.prompt_semibold, FontWeight.SemiBold),
    Font(R.font.prompt_bold, FontWeight.Bold),
)

/** Body text: easy to read in Thai at small sizes. */
private val Sarabun = FontFamily(
    Font(R.font.sarabun_regular, FontWeight.Normal),
    Font(R.font.sarabun_medium, FontWeight.Medium),
    Font(R.font.sarabun_semibold, FontWeight.SemiBold),
    Font(R.font.sarabun_bold, FontWeight.Bold),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Prompt),
        displayMedium = t.displayMedium.copy(fontFamily = Prompt),
        displaySmall = t.displaySmall.copy(fontFamily = Prompt),
        headlineLarge = t.headlineLarge.copy(fontFamily = Prompt, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Prompt, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Prompt, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Prompt, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Prompt, fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontFamily = Prompt, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = Sarabun),
        bodyMedium = t.bodyMedium.copy(fontFamily = Sarabun),
        bodySmall = t.bodySmall.copy(fontFamily = Sarabun),
        labelLarge = t.labelLarge.copy(fontFamily = Prompt, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily = Sarabun),
        labelSmall = t.labelSmall.copy(fontFamily = Sarabun),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Muted, jewel-toned status colours. They differ by mode because text must stay readable on both
 * backgrounds; the words on each chip and row still carry the meaning.
 */
object StatusColors {
    @Composable
    private fun shade(light: Long, dark: Long) = Color(if (isSystemInDarkTheme()) dark else light)

    val free: Color @Composable get() = shade(0xFF0F6B4F, 0xFF8FD3B6)
    val pending: Color @Composable get() = shade(0xFF8A5A00, 0xFFE3BE6E)
    val rejected: Color @Composable get() = shade(0xFF8E2A2A, 0xFFE59A9A)
    val cancelled: Color @Composable get() = shade(0xFF6B6A60, 0xFFB5B3A4)
    val noShow: Color @Composable get() = shade(0xFFA3461A, 0xFFEBA27F)
    val closure: Color @Composable get() = shade(0xFFD9D3C3, 0xFF3D4F47)
}

@Composable
fun RoomBookingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
