package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PhantomColorScheme = darkColorScheme(
    primary = NeonBloodRed,
    onPrimary = TextPrimary,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = CrimsonGlow,
    secondary = MatrixGreen,
    onSecondary = VoidBlack,
    secondaryContainer = GunmetalSurface,
    onSecondaryContainer = MatrixGreen,
    tertiary = AmberWarning,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonPanel,
    onSurfaceVariant = TextSecondary,
    outline = MetallicGrey,
    outlineVariant = MetallicHighlight,
    error = NeonBloodRed,
    onError = TextPrimary
)

val PhantomShapes = Shapes(
    extraSmall = CutCornerShape(4.dp),
    small = CutCornerShape(6.dp),
    medium = CutCornerShape(10.dp),
    large = CutCornerShape(14.dp),
    extraLarge = CutCornerShape(18.dp)
)

@Composable
fun PhantomTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PhantomColorScheme,
        typography = PhantomTypography,
        shapes = PhantomShapes,
        content = content
    )
}
