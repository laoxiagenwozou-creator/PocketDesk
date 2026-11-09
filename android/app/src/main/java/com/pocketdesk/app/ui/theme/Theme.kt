package com.pocketdesk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.pocketdesk.app.core.AppTheme

/**
 * Quiet utility palette: graphite surfaces, near-white text, one muted steel accent.
 * No filled bright CTAs, no gradient chrome — the touch surface is the only focus.
 */
private val Graphite = Color(0xFF14161A)
private val Slab = Color(0xFF1A1E23)
private val SlabHigh = Color(0xFF2B333C)
private val Line = Color(0xFF39424C)
private val Ink = Color(0xFFE9E6DF)
private val InkDim = Color(0xFF8D9299)
private val Steel = Color(0xFF7C93A8)
private val Clay = Color(0xFFD08C7E)

private val NightColors = darkColorScheme(
    primary = Ink,
    onPrimary = Graphite,
    secondary = Steel,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = Ink,
    surface = Slab,
    onSurface = Ink,
    surfaceVariant = SlabHigh,
    onSurfaceVariant = InkDim,
    outline = Line,
    error = Clay,
    onError = Graphite,
)

/**
 * Day skins: the same slab with the lights on — a tinted base, a white touch
 * surface, keys one step darker so they still read as keys. The three day
 * palettes differ only in their warm / cool / mossy families.
 */
private val SandColors = lightColorScheme(
    primary = Color(0xFF26231E),
    onPrimary = Color(0xFFFBF9F5),
    secondary = Color(0xFF5F7A87),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF26231E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF26231E),
    surfaceVariant = Color(0xFFE7E1D3),
    onSurfaceVariant = Color(0xFF5F594E),
    outline = Color(0xFFD9D2C4),
    error = Color(0xFFB4553F),
    onError = Color(0xFFFFFFFF),
)

private val SlateColors = lightColorScheme(
    primary = Color(0xFF1C2126),
    onPrimary = Color(0xFFF7F8FA),
    secondary = Color(0xFF4E7490),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF1F3F5),
    onBackground = Color(0xFF1C2126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2126),
    surfaceVariant = Color(0xFFE2E7ED),
    onSurfaceVariant = Color(0xFF556069),
    outline = Color(0xFFD2D8DF),
    error = Color(0xFFB4553F),
    onError = Color(0xFFFFFFFF),
)

private val MossColors = lightColorScheme(
    primary = Color(0xFF1F231E),
    onPrimary = Color(0xFFF8FAF5),
    secondary = Color(0xFF5C7260),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F4EF),
    onBackground = Color(0xFF1F231E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F231E),
    surfaceVariant = Color(0xFFE3E7DC),
    onSurfaceVariant = Color(0xFF565F52),
    outline = Color(0xFFD4D9CD),
    error = Color(0xFFB4553F),
    onError = Color(0xFFFFFFFF),
)

/**
 * Haptic (soft UI): one porcelain material with a single top-left light source.
 * Transplanted from the reference control surface — cool porcelain #e8ebf2 with
 * one mint accent; every other colour here is a shadow tone, so the palette has
 * to stay this narrow or the extrusion stops reading.
 */
private val HapticColors = lightColorScheme(
    primary = Color(0xFF333A48),
    onPrimary = Color(0xFFE9EDF4),
    secondary = Color(0xFF5FB68F),
    onSecondary = Color(0xFF17332A),
    background = Color(0xFFE7EBF2),
    onBackground = Color(0xFF333A48),
    surface = Color(0xFFE9EDF4),
    onSurface = Color(0xFF333A48),
    surfaceVariant = Color(0xFFE9EDF4),
    onSurfaceVariant = Color(0xFF6A7283),
    outline = Color(0xFFCFD6E0),
    error = Color(0xFFC05C4E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun PocketDeskTheme(theme: AppTheme = AppTheme.NIGHT, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        AppTheme.NIGHT -> NightColors
        AppTheme.SAND -> SandColors
        AppTheme.SLATE -> SlateColors
        AppTheme.MOSS -> MossColors
        AppTheme.HAPTIC -> HapticColors
    }
    CompositionLocalProvider(LocalNeumorph provides (theme == AppTheme.HAPTIC)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
