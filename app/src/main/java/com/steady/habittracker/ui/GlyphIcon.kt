package com.steady.habittracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * List-row glyph rendering that avoids Compose [androidx.compose.material3.Text] for emojis.
 *
 * Color emoji through the text pipeline is a major scroll-FPS cost (layout + Noto Color Emoji).
 * We rasterize each unique glyph once into a small [ImageBitmap] and draw it as an image —
 * the same idea as RemoteViews/widget lists, which stay smooth.
 */
object GlyphBitmapCache {
    /** ~256 glyphs × ~48² ARGB ≈ a few MB worst case; plenty for habit lists. */
    private val cache = object : LruCache<String, ImageBitmap>(256) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }
}

/** True if [s] is a plain letter/digit/simple symbol (cheap to draw as text). */
fun isSimpleGlyph(s: String): Boolean {
    if (s.isEmpty()) return true
    if (s.length == 1) {
        val c = s[0]
        return c.isLetterOrDigit() || c in "•◆✓○·-–—"
    }
    // Multi-codepoint almost always means emoji / ZWJ sequences
    return false
}

/**
 * Rasterize [glyph] into a square bitmap of [sizePx] using Android's emoji-capable text stack once.
 */
fun rasterizeGlyph(
    glyph: String,
    sizePx: Int,
    textColorArgb: Int = 0xFF000000.toInt()
): ImageBitmap {
    val bmp = Bitmap.createBitmap(sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = textColorArgb
        // Slightly smaller than the box so glyphs aren't clipped
        textSize = sizePx * 0.78f
        typeface = Typeface.DEFAULT
        isSubpixelText = true
    }
    val x = sizePx / 2f
    val y = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(glyph, x, y, paint)
    return bmp.asImageBitmap()
}

/**
 * Fast list icon: letter/simple → BasicText; emoji → cached bitmap Image.
 */
@Composable
fun GlyphIcon(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tintForSimple: Color = Color.Unspecified,
    contentDescription: String? = null
) {
    val g = glyph.trim().ifEmpty { "•" }
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx().coerceAtLeast(1) }

    if (isSimpleGlyph(g)) {
        val simpleColor = if (tintForSimple == Color.Unspecified) Color(0xFF94A3B8) else tintForSimple
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = g,
                style = TextStyle(
                    color = simpleColor,
                    fontSize = (size.value * 0.72f).sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        return
    }

    // Color emoji: bake once, reuse across rows / scrolls
    val key = "$g@$sizePx"
    val image = remember(key) {
        GlyphBitmapCache.get(key) ?: rasterizeGlyph(g, sizePx).also {
            GlyphBitmapCache.put(key, it)
        }
    }
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}

/**
 * Compact avatar: colored rounded square + glyph (letter or cached emoji).
 * Prefer this in Lazy lists over raw Material Text with emoji.
 */
@Composable
fun GlyphAvatar(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color,
    simpleTint: Color
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(
            glyph = glyph,
            size = size * 0.55f,
            tintForSimple = simpleTint
        )
    }
}
