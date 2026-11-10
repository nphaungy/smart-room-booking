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
 * Based on the KMITL Faculty of Science website (science.kmitl.ac.th): orange #FF7A00,
 * navy #021E4A as the second colour, on white. Text on the orange areas is navy rather than
 * white, because navy stays readable on bright orange (6:1 or more) where white would not (about 2.6:1).
 * These colours stay the same in light and dark mode; anything on the page uses the colour scheme.
 */
object Brand {
    /** The website's orange. */
    val Kmitl = Color(0xFFFF7A00)

    /** The website's navy. */
    val Navy = Color(0xFF021E4A)

    /** Text and icons on the orange areas. */
    val OnHero = Navy

    /** Secondary text on the orange areas (subtitles, times). */
    val OnHeroMuted = Color(0xCC021E4A)

    /** Text and icons on [Highlight]. */
    val Ink = Navy

    /** White for badges, chips and buttons on top of the orange areas. */
    val Highlight = Color.White

    /** Top bar and hero cards: a deeper orange than the website's banner, still fading lighter to the right. */
    val Hero = listOf(Color(0xFFE65C00), Color(0xFFF57200), Color(0xFFFF8C00))

    /** Icon tiles and stat cards. */
    val Accent = listOf(Color(0xFFFF7A00), Color(0xFFFFA200))
}

private val LightColors = lightColorScheme(
    // As light an orange as still gives 3:1 with white and on the page (buttons, links, bold labels)
    primary = Color(0xFFF26B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8D1),
    onPrimaryContainer = Color(0xFF5A2600),
    inversePrimary = Color(0xFFFFB066),
    secondary = Color(0xFF021E4A), // the website's navy
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F5),
    onSecondaryContainer = Color(0xFF021E4A),
    tertiary = Color(0xFF6B5446),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E3D8),
    onTertiaryContainer = Color(0xFF3F2A1C),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF2A1E18),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF2A1E18),
    surfaceVariant = Color(0xFFF3E6DC),
    onSurfaceVariant = Color(0xFF5F5049),
    surfaceTint = Color(0xFFF26B00),
    inverseSurface = Color(0xFF2A1E18),
    inverseOnSurface = Color(0xFFFBEFE7),
    error = Color(0xFF9B1C1C),
    onError = Color.White,
    errorContainer = Color(0xFFF7E4E4),
    onErrorContainer = Color(0xFF5F1111),
    outline = Color(0xFF8C7B70), // at least 3:1 on every light surface, so the edge of a field can be seen
    outlineVariant = Color(0xFFEBDDD2),
    surfaceBright = Color(0xFFFFF8F3),
    surfaceDim = Color(0xFFEDDFD5),
    // Each level is a step deeper into the warm white, so depth comes from tone rather than shadow.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFFBF1EA),
    surfaceContainerHigh = Color(0xFFF7EBE2),
    surfaceContainerHighest = Color(0xFFF2E4DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFA24D),
    onPrimary = Color(0xFF3A1A00),
    primaryContainer = Color(0xFF7A3A00),
    onPrimaryContainer = Color(0xFFFFE3C7),
    inversePrimary = Color(0xFFF26B00),
    secondary = Color(0xFFA9C2EA),
    onSecondary = Color(0xFF021E4A),
    secondaryContainer = Color(0xFF1C3A66),
    onSecondaryContainer = Color(0xFFDCE6F5),
    tertiary = Color(0xFFD9BBA8),
    onTertiary = Color(0xFF3A2617),
    tertiaryContainer = Color(0xFF4F3626),
    onTertiaryContainer = Color(0xFFF6E1D3),
    background = Color(0xFF1C130F),
    onBackground = Color(0xFFF3E6DC),
    surface = Color(0xFF1C130F),
    onSurface = Color(0xFFF3E6DC),
    surfaceVariant = Color(0xFF3A2A22),
    onSurfaceVariant = Color(0xFFC7B5AA),
    surfaceTint = Color(0xFFFFA24D),
    inverseSurface = Color(0xFFF3E6DC),
    inverseOnSurface = Color(0xFF2A1E18),
    error = Color(0xFFE8A0A0),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFF5D5D5),
    outline = Color(0xFF9A887D),
    outlineVariant = Color(0xFF4A362C),
    surfaceBright = Color(0xFF3F2D24),
    surfaceDim = Color(0xFF1C130F),
    surfaceContainerLowest = Color(0xFF170F0C),
    surfaceContainerLow = Color(0xFF231914),
    surfaceContainer = Color(0xFF271C16),
    surfaceContainerHigh = Color(0xFF2E2119),
    surfaceContainerHighest = Color(0xFF36271F),
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
 * Muted status colours. They differ by mode because text must stay readable on both
 * backgrounds; the words on each chip and row still carry the meaning.
 */
object StatusColors {
    @Composable
    private fun shade(light: Long, dark: Long) = Color(if (isSystemInDarkTheme()) dark else light)

    // Approved / checked in: the website's navy, so nothing on screen is green.
    val free: Color @Composable get() = shade(0xFF021E4A, 0xFFA9C2EA)
    val pending: Color @Composable get() = shade(0xFF8A5A00, 0xFFE3BE6E)
    val rejected: Color @Composable get() = shade(0xFF8E2A2A, 0xFFE59A9A)
    val cancelled: Color @Composable get() = shade(0xFF6B6A60, 0xFFB5B3A4)
    // Plum rather than orange, so "no-show" never looks like the app's own orange.
    val noShow: Color @Composable get() = shade(0xFF7B3F8C, 0xFFD7A8E3)
    val closure: Color @Composable get() = shade(0xFFD9D3C3, 0xFF4A3B33)
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
