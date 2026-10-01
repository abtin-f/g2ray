package com.abtin.tglass.ui.components

import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.features.main.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded bitmaps, keyed by path + requested size (about 1/8 of the heap). */
private object BitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) { cache.put(key, value) }
}

private fun decodeFile(path: String, maxPx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** Loads a local image file off the main thread, downscaled to roughly [maxPx]. */
@Composable
fun rememberFileImage(path: String?, maxPx: Int): ImageBitmap? {
    val key = path?.let { "$it@$maxPx" }
    val state = produceState(key?.let { BitmapCache.get(it) }, key) {
        if (key == null || path == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { decodeFile(path, maxPx) }?.also { BitmapCache.put(key, it) }
    }
    return state.value
}

/** Decoded minithumbnails by file id: a row scrolling back into view must not decode its JPEG on the main thread again. */
private val miniCache = LruCache<Int, ImageBitmap>(400)

@Composable
private fun rememberMini(fileId: Int, bytes: ByteArray?): ImageBitmap? = remember(fileId, bytes) {
    if (bytes == null) null
    else (if (fileId > 0) miniCache.get(fileId) else null) ?: runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()?.also { if (fileId > 0) miniCache.put(fileId, it) }
}

/**
 * Shows a Telegram image: the blurred inline preview first, then the real file fading in once it is
 * downloaded. Renders nothing when [image] is null, so callers can keep their placeholder underneath.
 */
@Composable
fun TgImage(image: ImageRef?, modifier: Modifier = Modifier, maxPx: Int = 1280, contentScale: ContentScale = ContentScale.Crop) {
    if (image == null) return
    val repo = LocalRepository.current
    val path = repo.filePath(image)
    LaunchedEffect(image.fileId, path) { if (path == null) repo.requestImage(image) }
    val full = rememberFileImage(path, maxPx)
    val mini = rememberMini(image.fileId, image.mini)
    // Starts at 1 when the bitmap was already cached, so revisited images don't flash.
    val alpha by animateFloatAsState(if (full != null) 1f else 0f, tween(220), label = "imageFade")
    Box(modifier) {
        if (mini != null && alpha < 1f) {
            Image(
                mini, null, Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(10.dp) else Modifier),
                contentScale = contentScale,
            )
        }
        if (full != null) {
            Image(full, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = contentScale)
        }
    }
}
