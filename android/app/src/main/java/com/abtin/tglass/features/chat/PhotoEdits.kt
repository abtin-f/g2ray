package com.abtin.tglass.features.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Photo editor model (Telegram iOS style: crop/rotate, drawing, text, adjustments).
 *
 * All geometry is normalized to the *rotated, uncropped* picture: (0,0) top-left, (1,1) bottom-right.
 * Stroke widths and text sizes are fractions of the picture's longer side, so they survive rotation and
 * scale the same in the on-screen preview and in the rendered file.
 */

val FullCrop = Rect(0f, 0f, 1f, 1f)

data class EditStroke(val points: List<Offset>, val color: Color, val width: Float)

data class EditText(
    val id: Long,
    val text: String,
    val color: Color,
    /** Drawn on a filled rounded box (text in a contrasting color). */
    val filled: Boolean,
    val center: Offset,
    /** Font size as a fraction of the picture's longer side. */
    val size: Float,
)

/** Immutable copy of [PhotoEdits], safe to hand to a background renderer. */
data class EditSpec(
    val quarterTurns: Int,
    val crop: Rect,
    val strokes: List<EditStroke>,
    val texts: List<EditText>,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
) {
    val hasAdjustments: Boolean get() = brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f
    val isEmpty: Boolean get() = quarterTurns % 4 == 0 && crop == FullCrop && strokes.isEmpty() && texts.isEmpty() && !hasAdjustments
}

/** Edits of one picked photo (observable). */
class PhotoEdits {
    /** Counter-clockwise quarter turns (the iOS "rotate left" button). */
    var quarterTurns by mutableIntStateOf(0)
    var crop by mutableStateOf(FullCrop)
    val strokes = mutableStateListOf<EditStroke>()
    val texts = mutableStateListOf<EditText>()
    var brightness by mutableFloatStateOf(0f)
    var contrast by mutableFloatStateOf(0f)
    var saturation by mutableFloatStateOf(0f)
    var warmth by mutableFloatStateOf(0f)

    fun spec() = EditSpec(quarterTurns, crop, strokes.toList(), texts.toList(), brightness, contrast, saturation, warmth)

    fun restore(s: EditSpec) {
        quarterTurns = s.quarterTurns
        crop = s.crop
        strokes.clear(); strokes.addAll(s.strokes)
        texts.clear(); texts.addAll(s.texts)
        brightness = s.brightness
        contrast = s.contrast
        saturation = s.saturation
        warmth = s.warmth
    }

    val isEmpty: Boolean get() = spec().isEmpty

    /** Rotates 90° counter-clockwise, carrying the crop, drawings and text positions along. */
    fun rotateLeft() {
        fun p(o: Offset) = Offset(o.y, 1f - o.x)
        val c = crop
        crop = Rect(c.top, 1f - c.right, c.bottom, 1f - c.left)
        val s = strokes.map { it.copy(points = it.points.map(::p)) }
        strokes.clear(); strokes.addAll(s)
        val t = texts.map { it.copy(center = p(it.center)) }
        texts.clear(); texts.addAll(t)
        quarterTurns = (quarterTurns + 1) % 4
    }

    fun updateText(id: Long, f: (EditText) -> EditText) {
        val i = texts.indexOfFirst { it.id == id }
        if (i >= 0) texts[i] = f(texts[i])
    }
}

/** 4×5 color matrix (Android/Compose layout, offsets in 0..255) for the adjust sliders (each −1..1). */
fun adjustMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float): FloatArray {
    val sat = 1f + saturation
    val lr = 0.2126f
    val lg = 0.7152f
    val lb = 0.0722f
    val satM = floatArrayOf(
        lr * (1 - sat) + sat, lg * (1 - sat), lb * (1 - sat), 0f, 0f,
        lr * (1 - sat), lg * (1 - sat) + sat, lb * (1 - sat), 0f, 0f,
        lr * (1 - sat), lg * (1 - sat), lb * (1 - sat) + sat, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    val k = 1f + contrast * 0.7f
    val off = 128f * (1f - k) + brightness * 80f
    val conM = floatArrayOf(
        k, 0f, 0f, 0f, off + warmth * 30f,
        0f, k, 0f, 0f, off + warmth * 6f,
        0f, 0f, k, 0f, off - warmth * 30f,
        0f, 0f, 0f, 1f, 0f,
    )
    return concatColorMatrix(satM, conM)
}

/** [b] applied after [a]. */
private fun concatColorMatrix(a: FloatArray, b: FloatArray): FloatArray {
    val r = FloatArray(20)
    for (i in 0 until 4) {
        for (j in 0 until 5) {
            var v = 0f
            for (k in 0 until 4) v += b[i * 5 + k] * a[k * 5 + j]
            if (j == 4) v += b[i * 5 + 4]
            r[i * 5 + j] = v
        }
    }
    return r
}

/** Text color on a filled text box. */
fun filledTextColor(box: Color): Color = if (box.luminance() > 0.6f) Color.Black else Color.White

/** Decodes [uri] upright (EXIF applied), downsampled so its longer side is at most about [maxSide]. */
fun decodeUpright(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    var bmp = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null
    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
    if (rotation != 0f || scale < 1f) {
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    bmp
}.getOrNull()

/**
 * Renders [spec] onto the photo at [uri] and writes a JPEG to the upload cache.
 * Returns a file:// Uri the normal send path (MediaPrep) can read, or null if the photo can't be decoded.
 */
suspend fun renderEditedPhoto(context: Context, uri: Uri, spec: EditSpec, maxSide: Int): Uri? = withContext(Dispatchers.Default) {
    runCatching {
        val src = decodeUpright(context, uri, maxSide) ?: return@runCatching null
        val turns = ((spec.quarterTurns % 4) + 4) % 4
        val rotated = if (turns != 0) {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(-90f * turns) }, true)
        } else src
        val w = rotated.width
        val h = rotated.height
        val c = spec.crop
        val cx = (c.left * w).roundToInt().coerceIn(0, w - 1)
        val cy = (c.top * h).roundToInt().coerceIn(0, h - 1)
        val cw = (c.width * w).roundToInt().coerceIn(1, w - cx)
        val ch = (c.height * h).roundToInt().coerceIn(1, h - cy)
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (spec.hasAdjustments) {
            paint.colorFilter = ColorMatrixColorFilter(adjustMatrix(spec.brightness, spec.contrast, spec.saturation, spec.warmth))
        }
        canvas.drawBitmap(rotated, android.graphics.Rect(cx, cy, cx + cw, cy + ch), android.graphics.Rect(0, 0, cw, ch), paint)
        canvas.translate(-cx.toFloat(), -cy.toFloat())
        val longSide = max(w, h).toFloat()

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        spec.strokes.forEach { s ->
            if (s.points.isEmpty()) return@forEach
            strokePaint.color = s.color.toArgb()
            strokePaint.strokeWidth = s.width * longSide
            if (s.points.size == 1) {
                val p = s.points[0]
                strokePaint.style = Paint.Style.FILL
                canvas.drawCircle(p.x * w, p.y * h, s.width * longSide / 2f, strokePaint)
                strokePaint.style = Paint.Style.STROKE
            } else {
                val path = android.graphics.Path()
                path.moveTo(s.points[0].x * w, s.points[0].y * h)
                for (i in 1 until s.points.size) path.lineTo(s.points[i].x * w, s.points[i].y * h)
                canvas.drawPath(path, strokePaint)
            }
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        spec.texts.forEach { t ->
            if (t.text.isBlank()) return@forEach
            val size = t.size * longSide
            textPaint.textSize = size
            val lines = t.text.split('\n')
            val lineH = textPaint.fontSpacing
            val widest = lines.maxOf { textPaint.measureText(it) }
            val centerX = t.center.x * w
            val centerY = t.center.y * h
            val top = centerY - lines.size * lineH / 2f
            if (t.filled) {
                val padH = size * 0.3f
                val padV = size * 0.15f
                boxPaint.color = t.color.toArgb()
                canvas.drawRoundRect(
                    RectF(centerX - widest / 2f - padH, top - padV, centerX + widest / 2f + padH, top + lines.size * lineH + padV),
                    size * 0.3f, size * 0.3f, boxPaint,
                )
                textPaint.color = filledTextColor(t.color).toArgb()
            } else {
                textPaint.color = t.color.toArgb()
                textPaint.setShadowLayer(size * 0.08f, 0f, size * 0.03f, 0x66000000)
            }
            val fm = textPaint.fontMetrics
            lines.forEachIndexed { i, line ->
                canvas.drawText(line, centerX, top + i * lineH - fm.ascent, textPaint)
            }
            textPaint.clearShadowLayer()
        }

        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        val file = File(dir, "edited_${System.nanoTime()}.jpg")
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        Uri.fromFile(file)
    }.getOrNull()
}
