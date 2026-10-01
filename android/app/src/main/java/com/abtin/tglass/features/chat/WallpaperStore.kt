package com.abtin.tglass.features.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Chat-background photos live in filesDir/wallpapers as screen-sized JPEGs. */
object WallpaperStore {
    private fun dir(context: Context): File = File(context.filesDir, "wallpapers").also { it.mkdirs() }

    /**
     * Copies the picked image into app storage, downscaled so it never exceeds the screen size x1.5, as a JPEG.
     * Returns the file name (relative to the wallpapers folder) or null. Call off the main thread.
     */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val dm = context.resources.displayMetrics
        val screenShort = min(dm.widthPixels, dm.heightPixels) * 1.5f
        val screenLong = max(dm.widthPixels, dm.heightPixels) * 1.5f
        fun scaleFor(w: Int, h: Int): Float =
            min(1f, min(screenShort / min(w, h), screenLong / max(w, h)))
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val k = scaleFor(info.size.width, info.size.height)
                if (k < 1f) decoder.setTargetSize((info.size.width * k).roundToInt().coerceAtLeast(1), (info.size.height * k).roundToInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val k = scaleFor(bounds.outWidth.coerceAtLeast(1), bounds.outHeight.coerceAtLeast(1))
            var sample = 1
            while (k * sample * 2 <= 1f) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("decode failed")
            val kk = scaleFor(raw.width, raw.height)
            if (kk < 1f) Bitmap.createScaledBitmap(raw, (raw.width * kk).roundToInt().coerceAtLeast(1), (raw.height * kk).roundToInt().coerceAtLeast(1), true) else raw
        }
        val name = "bg_${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(dir(context), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }.getOrNull()

    /** Decodes the stored photo (optionally blurred) off the main thread. */
    suspend fun load(context: Context, name: String, blur: Boolean): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val f = File(dir(context), name)
            val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
            if (bmp == null) null else (if (blur) blurred(bmp) else bmp).asImageBitmap()
        }.getOrNull()
    }

    /** Deletes every stored photo not named in [keep]. */
    fun prune(context: Context, keep: Collection<String?>) {
        val names = keep.filterNotNull().toSet()
        dir(context).listFiles()?.forEach { if (it.name !in names) it.delete() }
    }

    /**
     * A pre-blurred copy (iOS wallpaper "Blurred"): downscale to ~200 px wide, then three box-blur passes
     * (radius 4 here is about 20 px at screen size). One cached bitmap, no per-frame cost, works on every API level.
     */
    private fun blurred(src: Bitmap): Bitmap {
        val w = min(200, src.width)
        val h = max(1, (src.height * (w / src.width.toFloat())).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(px.size)
        repeat(3) {
            pass(px, tmp, w, h, 4, true)
            pass(tmp, px, w, h, 4, false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var ra = 0
            var ga = 0
            var ba = 0
            for (i in -r..r) {
                val p = src[base + i.coerceIn(0, len - 1) * step]
                ra += (p shr 16) and 255
                ga += (p shr 8) and 255
                ba += p and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] = (0xFF shl 24) or ((ra / div) shl 16) or ((ga / div) shl 8) or (ba / div)
                val add = src[base + min(i + r + 1, len - 1) * step]
                val sub = src[base + max(i - r, 0) * step]
                ra += ((add shr 16) and 255) - ((sub shr 16) and 255)
                ga += ((add shr 8) and 255) - ((sub shr 8) and 255)
                ba += (add and 255) - (sub and 255)
            }
        }
    }
}
