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

/** Vivid palette: indigo as the main colour, with violet, pink, amber and teal as accents. */
object Brand {
    val Indigo = Color(0xFF4338CA)
    val IndigoDark = Color(0xFF1E1B4B)
    val IndigoLight = Color(0xFFE0E7FF)
    val Violet = Color(0xFF7C3AED)
    val Pink = Color(0xFFBE185D)
    val Coral = Color(0xFFC2410C)
    val Amber = Color(0xFFF59E0B)
    val AmberLight = Color(0xFFFEF3C7)
    val Teal = Color(0xFF0F766E)
    val Sky = Color(0xFF0369A1)

    /** Light text for labels on top of the gradients below. */
    val Highlight = Color(0xFFFDE68A)

    // Every gradient is dark enough to carry white text.
    val Hero = listOf(Indigo, Violet, Pink)
    val Grape = listOf(Indigo, Violet)
    val Berry = listOf(Violet, Pink)
    val Sunset = listOf(Coral, Pink)
    val Ocean = listOf(Sky, Teal)
}

/** Each room type has its own colours, so the list can be scanned by colour. */
fun typeGradient(typeName: String): List<Color> = when {
    "ประชุม" in typeName -> Brand.Berry
    "คอมพิวเตอร์" in typeName -> Brand.Ocean
    "เรียน" in typeName -> Brand.Sunset
    else -> Brand.Grape
}

private val LightColors = lightColorScheme(
    primary = Brand.Indigo,
    onPrimary = Color.White,
    primaryContainer = Brand.IndigoLight,
    onPrimaryContainer = Brand.IndigoDark,
    secondary = Brand.Amber,
    onSecondary = Color(0xFF3B2600),
    secondaryContainer = Brand.AmberLight,
    onSecondaryContainer = Color(0xFF452F00),
    tertiary = Brand.Teal,
    background = Color(0xFFF5F6FF),
    onBackground = Color(0xFF1B1B2F),
    surface = Color(0xFFF5F6FF),
    onSurface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFFE8E9F8),
    onSurfaceVariant = Color(0xFF4B4F6B),
    outline = Color(0xFFB4B7D0),
    outlineVariant = Color(0xFFDDDFF0),
    // cards, menus and the bottom bar sit on white
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7D2FE),
    onPrimary = Brand.IndigoDark,
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Brand.IndigoLight,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3B2600),
    secondaryContainer = Color(0xFF78500A),
    onSecondaryContainer = Brand.AmberLight,
    tertiary = Color(0xFF5EEAD4),
    background = Color(0xFF0F0F23),
    surface = Color(0xFF0F0F23),
    surfaceVariant = Color(0xFF2A2A4A),
    surfaceContainerLowest = Color(0xFF16162E),
    surfaceContainerLow = Color(0xFF1A1A36),
    surfaceContainer = Color(0xFF1E1E3D),
    surfaceContainerHigh = Color(0xFF232345),
    surfaceContainerHighest = Color(0xFF28284D),
)

/** Headings: modern and bold, matches the vivid colours. Bundled so it looks the same on every device. */
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
        headlineLarge = t.headlineLarge.copy(fontFamily = Prompt, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Prompt, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Prompt, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = Prompt, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = Prompt, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Prompt, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = Sarabun),
        bodyMedium = t.bodyMedium.copy(fontFamily = Sarabun),
        bodySmall = t.bodySmall.copy(fontFamily = Sarabun),
        labelLarge = t.labelLarge.copy(fontFamily = Prompt, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = Sarabun),
        labelSmall = t.labelSmall.copy(fontFamily = Sarabun),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** Status colors used by chips and the room calendar (same meaning in light and dark). */
object StatusColors {
    val free = Color(0xFF2E8B57)
    val pending = Color(0xFFB86E00)
    val approved = Color(0xFFB83A33)
    val rejected = Color(0xFF7A7A7A)
    val cancelled = Color(0xFF9E9E9E)
    val closure = Color(0xFF5C6B7A)
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
