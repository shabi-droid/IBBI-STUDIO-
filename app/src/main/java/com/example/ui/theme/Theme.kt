package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimaryDark,
    onPrimary = CoralOnPrimaryDark,
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = CoralOnContainerDark,
    secondary = LavenderSecondaryDark,
    onSecondary = LavenderOnSecondaryDark,
    secondaryContainer = LavenderContainerDark,
    onSecondaryContainer = LavenderOnContainerDark,
    tertiary = SageTertiaryDark,
    onTertiary = SageOnTertiaryDark,
    tertiaryContainer = SageContainerDark,
    onTertiaryContainer = SageOnContainerDark,
    background = VelvetPlumBackgroundDark,
    onBackground = WarmCreamOnDark,
    surface = VelvetPlumSurfaceDark,
    onSurface = WarmCreamOnDark,
    surfaceVariant = VelvetPlumSurfaceVariantDark,
    onSurfaceVariant = WarmMutedDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = TerracottaOnPrimary,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaOnContainer,
    secondary = PlumSecondary,
    onSecondary = PlumOnSecondary,
    secondaryContainer = PlumContainer,
    onSecondaryContainer = PlumOnContainer,
    tertiary = SageTertiary,
    onTertiary = SageOnTertiary,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = SageOnContainer,
    background = AlabasterBackground,
    onBackground = CharcoalOnBackground,
    surface = CreamSurface,
    onSurface = CharcoalOnBackground,
    surfaceVariant = WarmSandSurfaceVariant,
    onSurfaceVariant = WarmMutedText,
    outline = WarmOutline,
    error = ErrorRed,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

@Composable
fun IbbiCircleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IbbiTypography,
        content = content
    )
}
