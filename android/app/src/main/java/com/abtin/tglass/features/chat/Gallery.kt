package com.abtin.tglass.features.chat

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.abtin.tglass.core.media.PickedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One item of the device gallery. */
data class GalleryItem(val uri: Uri, val video: Boolean, val durationMs: Long) {
    fun picked() = PickedMedia(uri, video)
}

/** Permissions needed to list the gallery (partial "selected photos" access counts too). */
fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun hasGalleryAccess(context: Context): Boolean {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/** Newest photos and videos, newest first. */
suspend fun loadRecentMedia(context: Context, limit: Int = 90): List<GalleryItem> = withContext(Dispatchers.IO) {
    val uri = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Video.VideoColumns.DURATION)
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
    val items = ArrayList<GalleryItem>()
    runCatching {
        context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durCol = c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            while (c.moveToNext() && items.size < limit) {
                val id = c.getLong(idCol)
                val video = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                items += GalleryItem(ContentUris.withAppendedId(base, id), video, if (durCol >= 0) c.getLong(durCol) else 0L)
            }
        }
    }
    items
}

private val thumbCache = LruCache<Uri, ImageBitmap>(120)

/** Square-ish thumbnail of a gallery item. */
@Composable
fun rememberGalleryThumb(item: GalleryItem, px: Int): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState(thumbCache.get(item.uri), item.uri) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(item.uri, Size(px, px), null).asImageBitmap()
                } else if (item.video) {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(item.uri), MediaStore.Video.Thumbnails.MINI_KIND, null)?.asImageBitmap()
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
                    context.contentResolver.openInputStream(item.uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }?.asImageBitmap()
                }
            }.getOrNull()
        }?.also { thumbCache.put(item.uri, it) }
    }
    return state.value
}

/** Gallery contents + access state, refreshed via [reload]. */
class GalleryState(initialAccess: Boolean) {
    var access by mutableStateOf(initialAccess)
    var items by mutableStateOf<List<GalleryItem>>(emptyList())
    var version by mutableIntStateOf(0)
    fun reload() { version++ }
}

@Composable
fun rememberGalleryState(): GalleryState {
    val context = LocalContext.current
    return remember { GalleryState(hasGalleryAccess(context)) }
}
