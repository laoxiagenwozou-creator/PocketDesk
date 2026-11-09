package com.pocketdesk.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketdesk.app.ui.theme.LocalNeumorph
import com.pocketdesk.app.ui.theme.neuRaised

/** Shared shapes: the touch surface and every key. */
internal val CardShape = RoundedCornerShape(24.dp)
internal val KeyShape = RoundedCornerShape(14.dp)

/**
 * A floating panel: quiet card with a hairline border on the classic skins, an
 * extruded slab on soft-UI. One helper so the menu, the host picker and every
 * other popover sit on the same plane.
 */
@Composable
internal fun PanelSurface(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    elevation: Dp = 10.dp,
    borderAlpha: Float = 0.6f,
    quiet: Boolean = false,
    content: @Composable () -> Unit,
) {
    val neu = LocalNeumorph.current
    if (quiet) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corner),
            shadowElevation = 4.dp,
            modifier = modifier,
        ) { content() }
    } else if (neu) {
        Box(
            modifier.neuRaised(
                corner = corner,
                surface = MaterialTheme.colorScheme.surface,
                offset = 5.dp,
                blur = 11.dp,
            ),
        ) { content() }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corner),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)),
            shadowElevation = elevation,
            modifier = modifier,
        ) { content() }
    }
}

/** Sliders follow the skin: steel/slate/moss accent, no default purple anywhere. */
@Composable
internal fun sliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.secondary,
    activeTrackColor = MaterialTheme.colorScheme.secondary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
internal fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun MenuEntry(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun KeyChip(
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    center: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable { onClick() },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            textAlign = if (center) TextAlign.Center else null,
            modifier = if (center) Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp)
            else Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
