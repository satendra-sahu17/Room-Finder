package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = PureWhite,
    primaryContainer = SkyContainer,
    onPrimaryContainer = DeepNavy,
    secondary = TealSecondary,
    onSecondary = PureWhite,
    secondaryContainer = TealContainer,
    onSecondaryContainer = DeepNavy,
    tertiary = AmberAccent,
    onTertiary = PureWhite,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = DeepNavy,
    background = PorcelainBg,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateOutline,
    outlineVariant = SlateOutlineSubtle,
    error = StatusRented,
    onError = PureWhite,
    errorContainer = StatusRentedBg,
    onErrorContainer = StatusRented
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyPrimaryDark,
    onPrimary = DeepNavy,
    primaryContainer = SkyContainerDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = CyanSecondaryDark,
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = AmberDark,
    onTertiary = DeepNavy,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberContainer,
    background = MidnightBg,
    onBackground = Color(0xFFF1F5F9),
    surface = MidnightSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = MidnightOutline,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = DeepNavy,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val RoomFinderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RoomFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RoomFinderTypography,
        shapes = RoomFinderShapes,
        content = content
    )
}
