package com.abtin.tglass.features.chat

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.abtin.tglass.R
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.design.UserBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of Telegram-iOS `SoftwareGradientBackground.swift`: up to 4 colors placed on 8 base positions,
 * a slight swirl, and cubic inverse-distance blending. Rendered into a tiny bitmap and scaled up,
 * exactly like Telegram does. Every sent message advances [phase] by one step (animated).
 * The base positions are in Telegram's y-up space (it writes `1 - y` before filling rows top-down).
 */
private val BasePositions = listOf(
    Offset(0.80f, 0.10f), Offset(0.60f, 0.20f), Offset(0.35f, 0.25f), Offset(0.25f, 0.60f),
    Offset(0.20f, 0.90f), Offset(0.40f, 0.80f), Offset(0.65f, 0.75f), Offset(0.75f, 0.40f),
)

private fun positions(phase: Int): List<Offset> {
    val p = ((phase % 8) + 8) % 8
    val shifted = BasePositions.drop(p) + BasePositions.take(p)
    return List(4) { shifted[it * 2] }
}

private const val GW = 48
private const val GH = 84

private fun renderGradient(pixels: IntArray, colors: List<Color>, pos: List<Offset>) {
    val cs = if (colors.isEmpty()) listOf(Color.Black) else colors
    if (cs.size == 1 || cs.distinct().size == 1) {
        val c = cs[0]
        val v = (0xFF shl 24) or ((c.red * 255f).roundToInt() shl 16) or ((c.green * 255f).roundToInt() shl 8) or (c.blue * 255f).roundToInt()
        pixels.fill(v)
        return
    }
    if (cs.size == 2) {
        // Two colors: a plain top-to-bottom gradient (Telegram's 2-color wallpapers).
        for (y in 0 until GH) {
            val t = y.toFloat() / (GH - 1)
            val r = ((cs[0].red + (cs[1].red - cs[0].red) * t) * 255f).roundToInt()
            val g = ((cs[0].green + (cs[1].green - cs[0].green) * t) * 255f).roundToInt()
            val b = ((cs[0].blue + (cs[1].blue - cs[0].blue) * t) * 255f).roundToInt()
            val v = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            for (x in 0 until GW) pixels[y * GW + x] = v
        }
        return
    }
    val n = min(cs.size, pos.size)
    val rgb = cs.map { floatArrayOf(it.red, it.green, it.blue) }
    for (y in 0 until GH) {
        val dy = y.toFloat() / GH - 0.5f
        val dy2 = dy * dy
        for (x in 0 until GW) {
            val dx = x.toFloat() / GW - 0.5f
            val center = sqrt(dx * dx + dy2)
            val swirl = 0.35f * center
            val theta = swirl * swirl * 0.8f * 8f
            val s = sin(theta)
            val c = cos(theta)
            val px = max(0f, min(1f, 0.5f + dx * c - dy * s))
            val py = max(0f, min(1f, 0.5f + dx * s + dy * c))
            var sum = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            for (i in 0 until n) {
                val ddx = px - pos[i].x
                val ddy = py - (1f - pos[i].y)
                var d = max(0f, 0.92f - sqrt(ddx * ddx + ddy * ddy))
                d = d * d * d
                sum += d
                r += d * rgb[i][0]; g += d * rgb[i][1]; b += d * rgb[i][2]
            }
            if (sum < 0.00001f) sum = 0.00001f
            val ri = min(255f, r / sum * 255f).toInt()
            val gi = min(255f, g / sum * 255f).toInt()
            val bi = min(255f, b / sum * 255f).toInt()
            pixels[y * GW + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
}

/** The wallpaper of the current theme: the user's own background for this day/night side, else the animated default. */
@Composable
fun ChatWallpaper(modifier: Modifier = Modifier, phase: Int = 0) {
    val dark = TgTheme.colors.isDark
    val bg = LocalAppSettings.current.backgroundFor(dark)
    WallpaperBackground(bg, modifier, phase)
}

/** Draws [bg]: photo, color/gradient, or (when neither) the theme's animated gradient with doodles. */
@Composable
fun WallpaperBackground(bg: UserBackground, modifier: Modifier = Modifier, phase: Int = 0) {
    val photo = bg.photo
    when {
        photo != null -> PhotoWallpaper(photo, bg.blur, bg.dim, modifier)
        bg.colors.isNotEmpty() -> ColorWallpaper(bg.colors, bg.rotation, bg.dim, modifier)
        else -> DefaultWallpaper(modifier, phase)
    }
}

@Composable
private fun PhotoWallpaper(photo: String, blur: Boolean, dim: Float, modifier: Modifier) {
    val context = LocalContext.current
    val fallback = TgTheme.colors.let { if (it.wallpaperInverted) Color.Black else it.wallpaper.first() }
    val image by produceState<ImageBitmap?>(null, photo, blur) { value = WallpaperStore.load(context, photo, blur) }
    Box(
        modifier.fillMaxSize().drawBehind {
            val img = image
            if (img == null) {
                drawRect(fallback)
            } else {
                // ContentScale.Crop by hand: one drawImage of the centered source window.
                val s = max(size.width / img.width, size.height / img.height)
                val sw = (size.width / s).roundToInt().coerceIn(1, img.width)
                val sh = (size.height / s).roundToInt().coerceIn(1, img.height)
                drawImage(
                    img,
                    srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
                    srcSize = IntSize(sw, sh),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        },
    )
}

@Composable
private fun ColorWallpaper(colors: List<Int>, rotation: Int, dim: Float, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val list = colors.map { Color(it) }
            val brush: Brush = if (list.size < 2 || list[0] == list[1]) SolidColor(list[0]) else {
                // Rotation 0 = top to bottom, then clockwise.
                val rad = Math.toRadians((rotation + 90).toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                Brush.linearGradient(list, Offset(cx - dx * half, cy - dy * half), Offset(cx + dx * half, cy + dy * half))
            }
            onDrawBehind {
                drawRect(brush)
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        },
    )
}

/** The theme's own wallpaper (gradient + pattern) regardless of the user's custom background (galleries, previews). */
@Composable
fun ThemeWallpaper(modifier: Modifier = Modifier) = DefaultWallpaper(modifier, 0)

/** How the pattern is blended (Telegram-iOS WallpaperBackgroundNode.updatePatternPresentation). */
private enum class PatternMode {
    /** Black pattern, CoreGraphics soft light at the intensity (light wallpapers). */
    LightSoftLight,
    /** White pattern, soft light (dark wallpapers). */
    DarkSoftLight,
    /** Pure black wallpaper: a plain white pattern at the intensity (patternIsBlack). */
    Plain,
    /** Negative intensity: black screen, the gradient visible only through the pattern at |intensity|. */
    Inverted,
}

/** One gradient frame and the color the pattern takes over it. */
private class WallpaperFrame(val gradient: ImageBitmap, val tint: Bitmap?)

/** Paint + shader reused across frames: the shader is only rebuilt when the frame or the size changes. */
private class PatternPainter {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var tint: Bitmap? = null
    private var w = 0f
    private var h = 0f

    fun prepare(tint: Bitmap?, w: Float, h: Float, alpha: Float) {
        if (tint !== this.tint || w != this.w || h != this.h) {
            this.tint = tint
            this.w = w
            this.h = h
            paint.shader = tint?.let {
                BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setScale(w / it.width, h / it.height) })
                }
            }
        }
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
    }
}

/**
 * The default Telegram gradient + pattern. The gradient (a 48x84 bitmap) is regenerated on a background thread while
 * it animates; the pattern is Telegram's own artwork as an alpha mask (rendered once per size, off the main thread)
 * colored by a shader that reproduces CoreGraphics' soft light over the gradient. A frame is two bitmap draws.
 */
@Composable
private fun DefaultWallpaper(modifier: Modifier, phase: Int) {
    val c = TgTheme.colors
    val context = LocalContext.current
    val animations = LocalAppSettings.current.animations
    val anim = remember { Animatable(phase.toFloat()) }
    LaunchedEffect(phase) {
        if (animations) anim.animateTo(phase.toFloat(), tween(500, easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)))
        else anim.snapTo(phase.toFloat())
    }
    val colors = c.wallpaper
    val patternColor = c.wallpaperPattern
    val patternAlpha = patternColor.alpha
    val mode = remember(colors, c.wallpaperInverted, patternColor) {
        val avg = colors.fold(0f) { acc, it -> acc + max(it.red, max(it.green, it.blue)) } / max(1, colors.size)
        when {
            c.wallpaperInverted -> PatternMode.Inverted
            avg <= 0.01f -> PatternMode.Plain
            patternColor.red < 0.5f -> PatternMode.LightSoftLight
            else -> PatternMode.DarkSoftLight
        }
    }
    var frame by remember { mutableStateOf<WallpaperFrame?>(null) }
    LaunchedEffect(colors, mode) {
        snapshotFlow { anim.value }.conflate().collect { f ->
            frame = withContext(Dispatchers.Default) { renderFrame(colors, f, mode) }
        }
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val showPattern = patternAlpha > 0f
    val mask by produceState<Bitmap?>(null, viewSize, showPattern) {
        value = if (!showPattern || viewSize.width <= 0 || viewSize.height <= 0) null
        else withContext(Dispatchers.Default) { PatternArt.mask(context, viewSize.width, viewSize.height) }
    }
    val painter = remember { PatternPainter() }
    val fallback = if (c.wallpaperInverted) Color.Black else colors.firstOrNull() ?: Color.Black
    Box(
        modifier.fillMaxSize().onSizeChanged { viewSize = it }.drawBehind {
            val w = this.size.width.roundToInt()
            val h = this.size.height.roundToInt()
            val fr = frame
            if (mode == PatternMode.Inverted) drawRect(Color.Black)
            else if (fr != null) drawImage(fr.gradient, srcSize = IntSize(GW, GH), dstSize = IntSize(w, h), filterQuality = FilterQuality.High)
            else drawRect(fallback)
            val m = mask
            if (m != null && fr != null && patternAlpha > 0f) {
                val tint = if (mode == PatternMode.Plain) null else fr.tint
                painter.prepare(tint, this.size.width, this.size.height, patternAlpha)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(m, null, android.graphics.RectF(0f, 0f, this.size.width, this.size.height), painter.paint)
                }
            }
        },
    )
}

private fun renderFrame(colors: List<Color>, f: Float, mode: PatternMode): WallpaperFrame {
    val a = floor(f).toInt()
    val t = f - a
    val p0 = positions(a)
    val p1 = positions(a + 1)
    val pos = List(4) { i -> Offset(p0[i].x + (p1[i].x - p0[i].x) * t, p0[i].y + (p1[i].y - p0[i].y) * t) }
    val pixels = IntArray(GW * GH)
    renderGradient(pixels, colors, pos)
    val gradient = Bitmap.createBitmap(pixels, GW, GH, Bitmap.Config.ARGB_8888)
    val tint: Bitmap? = when (mode) {
        PatternMode.Inverted -> gradient
        PatternMode.Plain -> null
        PatternMode.LightSoftLight, PatternMode.DarkSoftLight -> {
            // CoreGraphics soft light of a black (white) pattern over backdrop Cb is Cb - a*Cb*(1-Cb) (Cb + a*Cb*(1-Cb)),
            // which is exactly the pattern drawn normally at alpha a in the color Cb*Cb (Cb*(2-Cb)).
            val light = mode == PatternMode.LightSoftLight
            val out = IntArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                out[i] = (0xFF shl 24) or (soft((p shr 16) and 0xFF, light) shl 16) or (soft((p shr 8) and 0xFF, light) shl 8) or soft(p and 0xFF, light)
            }
            Bitmap.createBitmap(out, GW, GH, Bitmap.Config.ARGB_8888)
        }
    }
    return WallpaperFrame(gradient.asImageBitmap(), tint)
}

private fun soft(cb: Int, light: Boolean): Int {
    val b = cb / 255f
    val v = if (light) b * b else b * (2f - b)
    return (v * 255f + 0.5f).toInt().coerceIn(0, 255)
}

/**
 * Telegram's chat pattern: Telegram-iOS `submodules/TelegramUI/Resources/fqv01SQemVIBAAAApND8LDRUhRU.tgv`
 * (res/raw/tg_chat_pattern.tgv), a gzipped 1440 x 2960 SVG of 745 filled paths. Parsed once; drawn aspect-filled
 * and centered into an ALPHA_8 mask per size, like Telegram's patternWallpaperImage (displayMode .aspectFill).
 */
internal object PatternArt {
    private const val ArtW = 1440f
    private const val ArtH = 2960f
    private var paths: List<android.graphics.Path>? = null
    private var failed = false
    private val masks = LinkedHashMap<Long, Bitmap>(8, 0.75f, true)

    private fun paths(context: android.content.Context): List<android.graphics.Path>? {
        paths?.let { return it }
        if (failed) return null
        val parsed = runCatching {
            val svg = java.util.zip.GZIPInputStream(context.resources.openRawResource(R.raw.tg_chat_pattern)).bufferedReader().use { it.readText() }
            Regex("\\sd=\"([^\"]*)\"").findAll(svg).map { svgPath(it.groupValues[1]) }.toList()
        }.getOrNull()
        if (parsed.isNullOrEmpty()) {
            failed = true
            return null
        }
        paths = parsed
        return parsed
    }

    /** The pattern as an alpha mask of [w] x [h] px, or null if the artwork can't be read. */
    @Synchronized
    fun mask(context: android.content.Context, w: Int, h: Int): Bitmap? {
        val key = (w.toLong() shl 32) or h.toLong()
        masks[key]?.let { return it }
        val list = paths(context) ?: return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = android.graphics.Canvas(bmp)
        val s = max(w / ArtW, h / ArtH)
        canvas.translate((w - ArtW * s) / 2f, (h - ArtH * s) / 2f)
        canvas.scale(s, s)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        list.forEach { canvas.drawPath(it, paint) }
        masks[key] = bmp
        while (masks.size > 4) masks.remove(masks.keys.first())
        return bmp
    }
}

private val SvgToken = Regex("[MmLlHhVvCcSsZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)")

/** SVG path data (the commands the pattern uses: M L H V C S Z, absolute and relative) to an android Path. */
private fun svgPath(d: String): android.graphics.Path {
    val path = android.graphics.Path()
    val a = FloatArray(6)
    var n = 0
    var cmd = 'M'
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var ctrlX = 0f
    var ctrlY = 0f
    var lastCubic = false
    for (m in SvgToken.findAll(d)) {
        val t = m.value
        val ch = t[0]
        if (ch.isLetter()) {
            cmd = ch
            n = 0
            if (ch == 'z' || ch == 'Z') {
                path.close()
                x = startX; y = startY; lastCubic = false
            }
            continue
        }
        if (n >= 6) n = 0
        a[n++] = t.toFloat()
        val arity = when (cmd) {
            'M', 'm', 'L', 'l' -> 2
            'H', 'h', 'V', 'v' -> 1
            'C', 'c' -> 6
            'S', 's' -> 4
            else -> 0
        }
        if (arity == 0 || n < arity) continue
        n = 0
        when (cmd) {
            'M' -> { x = a[0]; y = a[1]; path.moveTo(x, y); startX = x; startY = y; lastCubic = false; cmd = 'L' }
            'm' -> { x += a[0]; y += a[1]; path.moveTo(x, y); startX = x; startY = y; lastCubic = false; cmd = 'l' }
            'L' -> { x = a[0]; y = a[1]; path.lineTo(x, y); lastCubic = false }
            'l' -> { x += a[0]; y += a[1]; path.lineTo(x, y); lastCubic = false }
            'H' -> { x = a[0]; path.lineTo(x, y); lastCubic = false }
            'h' -> { x += a[0]; path.lineTo(x, y); lastCubic = false }
            'V' -> { y = a[0]; path.lineTo(x, y); lastCubic = false }
            'v' -> { y += a[0]; path.lineTo(x, y); lastCubic = false }
            'C' -> {
                path.cubicTo(a[0], a[1], a[2], a[3], a[4], a[5])
                ctrlX = a[2]; ctrlY = a[3]; x = a[4]; y = a[5]; lastCubic = true
            }
            'c' -> {
                path.cubicTo(x + a[0], y + a[1], x + a[2], y + a[3], x + a[4], y + a[5])
                ctrlX = x + a[2]; ctrlY = y + a[3]; x += a[4]; y += a[5]; lastCubic = true
            }
            'S', 's' -> {
                val c1x = if (lastCubic) 2f * x - ctrlX else x
                val c1y = if (lastCubic) 2f * y - ctrlY else y
                val rel = cmd == 's'
                val c2x = if (rel) x + a[0] else a[0]
                val c2y = if (rel) y + a[1] else a[1]
                val ex = if (rel) x + a[2] else a[2]
                val ey = if (rel) y + a[3] else a[3]
                path.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                ctrlX = c2x; ctrlY = c2y; x = ex; y = ey; lastCubic = true
            }
        }
    }
    return path
}
