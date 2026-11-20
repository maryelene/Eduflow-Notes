package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = RosePrimary,
    onPrimary = RoseOnPrimary,
    primaryContainer = RosePrimaryContainer,
    onPrimaryContainer = RoseOnPrimaryContainer,
    secondary = BlushSecondary,
    onSecondary = BlushOnSecondary,
    secondaryContainer = BlushSecondaryContainer,
    onSecondaryContainer = BlushOnSecondaryContainer,
    tertiary = CoralTertiary,
    onTertiary = CoralOnTertiary,
    tertiaryContainer = CoralTertiaryContainer,
    onTertiaryContainer = CoralOnTertiaryContainer,
    background = RosePaperBackground,
    onBackground = RosePaperOnBackground,
    surface = RoseSurface,
    onSurface = RoseOnSurface,
    surfaceVariant = RoseSurfaceVariant,
    onSurfaceVariant = RoseOnSurfaceVariant,
    outline = RoseOutline,
    outlineVariant = RoseOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPinkPrimary,
    onPrimary = DarkPinkOnPrimary,
    primaryContainer = DarkPinkPrimaryContainer,
    onPrimaryContainer = DarkPinkOnPrimaryContainer,
    secondary = DarkBlushSecondary,
    onSecondary = DarkBlushOnSecondary,
    secondaryContainer = DarkBlushSecondaryContainer,
    onSecondaryContainer = DarkBlushOnSecondaryContainer,
    tertiary = DarkCoralTertiary,
    onTertiary = DarkCoralOnTertiary,
    tertiaryContainer = DarkCoralTertiaryContainer,
    onTertiaryContainer = DarkCoralOnTertiaryContainer,
    background = DarkBerryBackground,
    onBackground = DarkBerryOnBackground,
    surface = DarkBerrySurface,
    onSurface = DarkBerryOnSurface,
    surfaceVariant = DarkBerrySurfaceVariant,
    onSurfaceVariant = DarkBerryOnSurfaceVariant,
    outline = DarkBerryOutline,
    outlineVariant = DarkBerryOutlineVariant
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun EduFlowNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
