package dev.batterymirror.z70.ui.theme

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

/** 【配色】深色偏墨黑，亮色用暖纸色，数字用等宽特性对齐。 */
private val Ink = Color(0xFF0C0F0E)
private val InkRaised = Color(0xFF151A18)
private val InkCard = Color(0xFF1B221F)
private val Line = Color(0xFF2C3833)
private val Lime = Color(0xFFD6F25C)
private val Mist = Color(0xFFA7B2AB)
private val Paper = Color(0xFFF4F1E8)
private val InkText = Color(0xFFF3F6F1)

val Amber = Color(0xFFFFC56B)
val Coral = Color(0xFFFF6B5A)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF1A2208),
    secondary = Color(0xFF7DFFC3),
    onSecondary = Color(0xFF042218),
    tertiary = Amber,
    onTertiary = Color(0xFF2A1C06),
    background = Ink,
    onBackground = InkText,
    surface = InkRaised,
    onSurface = InkText,
    surfaceVariant = InkCard,
    onSurfaceVariant = Mist,
    outline = Line,
    error = Coral,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6A32),
    onPrimary = Color.White,
    secondary = Color(0xFF0E6B4A),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Color(0xFF14211B),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF14211B),
    surfaceVariant = Color(0xFFE7EDE4),
    onSurfaceVariant = Color(0xFF3E4A43),
    outline = Color(0xFFD0D8D0),
    error = Color(0xFFB42318),
    onError = Color.White,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 64.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.4).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun BatteryMirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
