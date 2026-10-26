package com.havila.plannie.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PlannieColorScheme = lightColorScheme(
    primary = PlannieGreen800,
    onPrimary = PlannieCard,
    primaryContainer = PlannieGreen100,
    onPrimaryContainer = PlannieGreen900,
    secondary = PlannieGreen600,
    onSecondary = PlannieCard,
    secondaryContainer = PlannieGreen50,
    onSecondaryContainer = PlannieGreen900,
    tertiary = PlannieGreen400,
    background = PlannieBg,
    onBackground = PlannieText,
    surface = PlannieCard,
    onSurface = PlannieText,
    surfaceVariant = PlannieGreen50,
    onSurfaceVariant = PlannieText2,
)

@Composable
fun PlannieTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PlannieColorScheme,
        typography = Typography,
        content = content
    )
}
