package com.abtin.tglass.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.MessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** A picture or video the user picked to send. */
data class PickedMedia(val uri: Uri, val video: Boolean)

/**
 * Turns picked gallery/camera items into ready-to-send local files in the cache:
 * photos are rotated upright and re-encoded (max 2560 px, like Telegram), videos are copied
 * and get a 320 px JPEG thumbnail plus their size and duration.
 */
object MediaPrep {
    const val MAX_PHOTO = 2560

    /** Longest side of a photo sent without "HD" (Telegram's standard quality). */
    const val STANDARD_PHOTO = 1280

    /** [maxPhotoSide]: 2560 for HD (the default), [STANDARD_PHOTO] for standard quality. */
    suspend fun prepare(context: Context, items: List<PickedMedia>, caption: String?, maxPhotoSide: Int = MAX_PHOTO): List<MessageContent.Photo> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        items.mapIndexedNotNull { i, item ->
            runCatching {
                val cap = caption?.takeIf { i == 0 && it.isNotBlank() }
                if (item.video) prepareVideo(context, item.uri, dir, cap) else preparePhoto(context, item.uri, dir, cap, maxPhotoSide)
            }.getOrNull()
        }
    }

    private fun preparePhoto(context: Context, uri: Uri, dir: File, caption: String?, maxSide: Int = MAX_PHOTO): MessageContent.Photo? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val out = File(dir, "photo_${System.nanoTime()}.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 87, it) }
        return MessageContent.Photo(
            seed = out.hashCode(),
            aspect = bmp.width.toFloat() / bmp.height,
            caption = caption,
            image = ImageRef(0, out.absolutePath, null, bmp.width, bmp.height),
        )
    }

    private fun prepareVideo(context: Context, uri: Uri, dir: File, caption: String?): MessageContent.Photo? {
        val out = File(dir, "video_${System.nanoTime()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(out).use { input.copyTo(it) } } ?: return null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val duration = ((r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt()
            val frame = r.getFrameAtTime(0)
            val thumb = frame?.let { f ->
                val s = 320f / maxOf(f.width, f.height)
                val scaled = if (s < 1f) Bitmap.createScaledBitmap(f, (f.width * s).toInt(), (f.height * s).toInt(), true) else f
                File(dir, "thumb_${System.nanoTime()}.jpg").also { t -> FileOutputStream(t).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) } } to scaled
            }
            return MessageContent.Photo(
                seed = out.hashCode(),
                aspect = if (w > 0 && h > 0) w.toFloat() / h else 1.6f,
                caption = caption,
                emoji = "🎬",
                image = thumb?.let { (file, bmp) -> ImageRef(0, file.absolutePath, null, bmp.width, bmp.height) },
                video = true,
                videoFile = ImageRef(0, out.absolutePath, null, w, h),
                duration = duration,
            )
        } finally {
            r.release()
        }
    }
}
