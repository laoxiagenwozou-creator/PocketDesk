package com.pocketdesk.app.ui.theme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.lang.ref.WeakReference
import kotlin.math.ceil

/**
 * Soft-UI ("haptic") skin support.
 *
 * One light source, top-left. Every control is extruded from the same porcelain:
 * a raised piece is a body with a white shadow up-left and a grey shadow down-right;
 * a pressed piece is the same body with those shadows turned inwards. There are
 * no borders anywhere — the shape is read from the shadows alone.
 *
 * Values are lifted from the reference control surface:
 *   raise      16px 16px 34px #c7cdd9, -16px -16px 34px #ffffff
 *   raise-sm    8px  8px 18px #cbd1dc,  -8px  -8px 18px #ffffff
 *   inset        inset 5px 5px 11px #cbd1dc, inset -5px -5px 11px #ffffff
 * scaled down to phone-sized pieces (a thumb pad is ~78dp, not 300px).
 */
val LocalNeumorph = staticCompositionLocalOf { false }

/** The tone a pressed soft-UI piece sinks into. One place, so a sixth skin can move it. */
val NeuSunk = Color(0xFFD7DDE8)

private val RaiseDark = Color(0xFFC7CDD9)
private val InsetDark = Color(0xFFC5CBD7)
private val Light = Color(0xFFFFFFFF)

private fun softPaint(color: Color, radiusPx: Float, alpha: Float = 1f): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        if (radiusPx > 0f) maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
    }

private fun Paint.solid(color: Color): Paint = apply { this.color = color.toArgb() }

private fun bodyPaint(color: Color): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }

/** Corner radius is clamped to a pill so callers can pass CircleShape-sized values. */
private fun CacheDrawScope.pxCorner(corner: Dp): Float =
    corner.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)

/**
 * An extruded slab: two soft shadows behind a rounded body. Draws nothing but
 * shadows and the body, so put it under the content (it is a drawBehind).
 */
fun Modifier.neuRaised(
    corner: Dp,
    surface: Color,
    offset: Dp = 3.5.dp,
    blur: Dp = 8.dp,
): Modifier = drawWithCache {
    val r = pxCorner(corner)
    val o = offset.toPx()
    val dark = softPaint(RaiseDark, blur.toPx())
    val light = softPaint(Light, blur.toPx())
    val body = bodyPaint(surface)
    onDrawBehind {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.drawRoundRect(o, o, size.width + o, size.height + o, r, r, dark)
            c.drawRoundRect(-o, -o, size.width - o, size.height - o, r, r, light)
            c.drawRoundRect(0f, 0f, size.width, size.height, r, r, body)
        }
    }
}

/**
 * A recessed well: the body sits below the surface and the shadows fall inwards.
 * Clip first so the bands stop at the rim, then the body, then the bands.
 */
fun Modifier.neuInset(
    corner: Dp,
    surface: Color,
    offset: Dp = 3.dp,
    blur: Dp = 6.dp,
): Modifier = clip(RoundedCornerShape(corner))
    .background(surface)
    .drawWithCache {
        val bands = InnerBands(size.width, size.height, pxCorner(corner), offset.toPx(), blur.toPx())
        onDrawBehind { drawIntoCanvas { bands.draw(it.nativeCanvas) } }
    }

private class InnerBands(width: Float, height: Float, corner: Float, offset: Float, blur: Float) {
    private val radius = CornerRadius(corner, corner)
    val outline = Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, width, height), radius))
    }.asAndroidPath()
    val top = band(width, height, offset)
    val bottom = band(width, height, -offset)
    private val topPaint = softPaint(InsetDark, blur)
    private val bottomPaint = softPaint(Light, blur)

    private fun band(width: Float, height: Float, offset: Float): android.graphics.Path {
        val hole = Path().apply {
            addRoundRect(RoundRect(Rect(offset, offset, width + offset, height + offset), radius))
        }
        return Path().apply {
            addRect(Rect(-width, -height, width * 2f, height * 2f))
            op(this, hole, PathOperation.Difference)
        }.asAndroidPath()
    }

    fun draw(canvas: android.graphics.Canvas, topAlpha: Float = 1f, bottomAlpha: Float = 0.9f) {
        topPaint.alpha = (topAlpha * 255).toInt()
        bottomPaint.alpha = (bottomAlpha * 255).toInt()
        val checkpoint = canvas.save()
        try {
            canvas.clipPath(outline)
            canvas.drawPath(top, topPaint)
            canvas.drawPath(bottom, bottomPaint)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }
}

private data class PressedShadowKey(
    val width: Float,
    val height: Float,
    val corner: Float,
    val offset: Float,
    val blur: Float,
)

private data class ShadowMaskSize(val width: Int, val height: Int, val padding: Int) {
    val outerWidth = width + padding * 2
    val outerHeight = height + padding * 2
}

private fun PressedShadowKey.maskSize(): ShadowMaskSize? {
    if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite() || !blur.isFinite()) return null
    if (width > 2048f || height > 2048f || blur > 256f) return null
    val dimensions = ShadowMaskSize(
        ceil(width).toInt(),
        ceil(height).toInt(),
        ceil(blur.coerceAtLeast(0f) * 3f).toInt() + 2,
    )
    val outerBytes = ((dimensions.outerWidth + 3L) / 4L * 4L) * dimensions.outerHeight
    val innerBytes = ((dimensions.width + 3L) / 4L * 4L) * dimensions.height * 2L
    return dimensions.takeIf { outerBytes + innerBytes <= 768L * 1024L }
}

private class PressedShadowMasks(key: PressedShadowKey, dimensions: ShadowMaskSize, bands: InnerBands) {
    val padding = dimensions.padding.toFloat()
    val outer = Bitmap.createBitmap(dimensions.outerWidth, dimensions.outerHeight, Bitmap.Config.ALPHA_8).apply {
        density = Bitmap.DENSITY_NONE
        android.graphics.Canvas(this).apply {
            translate(padding, padding)
            drawRoundRect(0f, 0f, key.width, key.height, key.corner, key.corner, softPaint(Light, key.blur))
        }
    }
    private val innerPaint = softPaint(Light, key.blur * 0.7f)
    val top = innerMask(dimensions, bands.top)
    val bottom = innerMask(dimensions, bands.bottom)
    val bytes = outer.allocationByteCount + top.allocationByteCount + bottom.allocationByteCount

    private fun innerMask(dimensions: ShadowMaskSize, path: android.graphics.Path): Bitmap =
        Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ALPHA_8).apply {
            density = Bitmap.DENSITY_NONE
            android.graphics.Canvas(this).drawPath(path, innerPaint)
        }
}

private val pressedShadowCache = object : LruCache<PressedShadowKey, PressedShadowMasks>(4 * 1024 * 1024) {
    override fun sizeOf(key: PressedShadowKey, value: PressedShadowMasks): Int = value.bytes
}

private fun pressedShadows(key: PressedShadowKey, dimensions: ShadowMaskSize, bands: InnerBands): PressedShadowMasks =
    pressedShadowCache.get(key) ?: PressedShadowMasks(key, dimensions, bands).also { pressedShadowCache.put(key, it) }

/**
 * A control that physically travels: at [press] = 0 it is raised, at 1 it is
 * sunk. The raised shadows fade out while the inner bands fade in, and the body
 * dips a shade — the same travel an Arcade button makes, without moving layout.
 */
fun Modifier.neuPressed(
    press: Float,
    corner: Dp,
    surface: Color,
    sunk: Color,
    offset: Dp = 4.dp,
    blur: Dp = 8.dp,
): Modifier = composed {
    val currentPress = rememberUpdatedState(press)
    val currentSurface = rememberUpdatedState(surface)
    val currentSunk = rememberUpdatedState(sunk)
    remember(corner, offset, blur) {
        Modifier.drawWithCache {
            val r = pxCorner(corner)
            val o = offset.toPx()
            val b = blur.toPx()
            val key = PressedShadowKey(size.width, size.height, r, o, b)
            val dimensions = key.maskSize()
            val bands = InnerBands(size.width, size.height, r, o * 0.8f, b * 0.7f)
            var masks = WeakReference(dimensions?.let { pressedShadows(key, it, bands) })
            val dark = softPaint(RaiseDark, b)
            val light = softPaint(Light, b)
            val tint = bodyPaint(Light).apply { isFilterBitmap = true }
            val body = bodyPaint(surface)
            onDrawBehind {
                val p = currentPress.value.coerceIn(0f, 1f)
                val cached = if (dimensions == null) null else masks.get()
                    ?: pressedShadows(key, dimensions, bands).also { masks = WeakReference(it) }
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    if (p < 1f) {
                        val travel = o * (1f - 0.45f * p)
                        val alpha = ((1f - p) * 255).toInt()
                        if (cached != null) {
                            tint.color = RaiseDark.toArgb()
                            tint.alpha = alpha
                            c.drawBitmap(cached.outer, travel - cached.padding, travel - cached.padding, tint)
                            tint.color = Light.toArgb()
                            tint.alpha = alpha
                            c.drawBitmap(cached.outer, -travel - cached.padding, -travel - cached.padding, tint)
                        } else {
                            dark.alpha = alpha
                            light.alpha = alpha
                            c.drawRoundRect(travel, travel, size.width + travel, size.height + travel, r, r, dark)
                            c.drawRoundRect(-travel, -travel, size.width - travel, size.height - travel, r, r, light)
                        }
                    }
                    body.solid(lerpColor(currentSurface.value, currentSunk.value, p * 0.85f))
                    c.drawRoundRect(0f, 0f, size.width, size.height, r, r, body)
                    if (p > 0f) {
                        if (cached != null) {
                            val checkpoint = c.save()
                            try {
                                c.clipPath(bands.outline)
                                tint.color = InsetDark.toArgb()
                                tint.alpha = (p * 255).toInt()
                                c.drawBitmap(cached.top, 0f, 0f, tint)
                                tint.color = Light.toArgb()
                                tint.alpha = (p * 0.9f * 255).toInt()
                                c.drawBitmap(cached.bottom, 0f, 0f, tint)
                            } finally {
                                c.restoreToCount(checkpoint)
                            }
                        } else {
                            bands.draw(c, p, p * 0.9f)
                        }
                    }
                }
            }
        }
    }
}

/** A raised circle, for dial-like pieces. */
fun Modifier.neuDial(
    surface: Color,
    offset: Dp = 4.dp,
    blur: Dp = 9.dp,
): Modifier = drawBehind {
    val o = offset.toPx()
    val b = blur.toPx()
    val r = minOf(size.width, size.height) / 2f
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        c.drawCircle(r + o * 0.7f, r + o * 0.7f, r, softPaint(RaiseDark, b))
        c.drawCircle(r - o * 0.7f, r - o * 0.7f, r, softPaint(Light, b))
        c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).solid(surface))
    }
}

/**
 * The studio backdrop: a pale sheet with a faint dot grid. Drawn as one tiled
 * shader so the whole screen costs a single draw call.
 */
@Composable
fun Modifier.neuDots(
    color: Color,
    spacing: Dp = 6.dp,
    dotRadius: Dp = 0.5.dp,
): Modifier {
    val density = LocalDensity.current
    val paint = remember(color, spacing, dotRadius, density) {
        val tile = with(density) { spacing.roundToPx() }.coerceAtLeast(2)
        val radius = with(density) { dotRadius.toPx() }
        val bmp = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
        canvas.drawCircle(tile / 2f, tile / 2f, radius, dot)
        // Plain android.graphics here on purpose — the consumer is a native Paint.
        val shader = android.graphics.BitmapShader(
            bmp,
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT,
        )
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    }
    return drawBehind {
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

internal fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)
