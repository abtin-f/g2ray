package com.abtin.tglass.core.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/** Hand-offs to other apps: share sheet, gallery, SMS invites and the official Telegram app. */
object Sharing {
    const val InviteText = "Hey, let's chat on Telegram! It's fast, free and secure. Download it here: https://telegram.org/dl"

    /** Packages of Telegram apps that can place calls (see the <queries> block in the manifest). */
    private val TelegramPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta", "org.thunderdog.challegram")

    fun mimeOf(path: String, fallback: String = "*/*"): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(path).extension.lowercase(Locale.US)) ?: fallback

    /** Android share sheet with plain text. */
    fun shareText(context: Context, text: String, title: String? = null): Boolean = start(
        context,
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title),
    )

    /** Android share sheet with a local file (served through the app's FileProvider). */
    suspend fun shareFile(context: Context, path: String, mime: String): Boolean {
        val uri = withContext(Dispatchers.IO) { shareableUri(context, File(path)) } ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, chooser)
    }

    /**
     * A content:// uri for [file]. Files outside the FileProvider roots (e.g. somewhere else in the cache)
     * are copied into cache/upload/ first.
     */
    private fun shareableUri(context: Context, file: File): Uri? {
        if (!file.exists()) return null
        val authority = "${context.packageName}.files"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            runCatching {
                val dir = File(context.cacheDir, "upload/share_${System.nanoTime()}").apply { mkdirs() }
                val copy = File(dir, file.name)
                file.copyTo(copy, overwrite = true)
                FileProvider.getUriForFile(context, authority, copy)
            }.getOrNull()
        }
    }

    /** Whether saving to the gallery needs WRITE_EXTERNAL_STORAGE (Android 8–9 only). */
    val saveNeedsPermission: Boolean get() = Build.VERSION.SDK_INT < 29

    /**
     * Copies a downloaded photo/video into the shared gallery (Pictures/TGlass or Movies/TGlass).
     * Returns false when it could not be written.
     */
    suspend fun saveToGallery(context: Context, path: String, video: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(path)
            if (!src.exists()) return@runCatching false
            val ext = src.extension.lowercase(Locale.US).ifBlank { if (video) "mp4" else "jpg" }
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: if (video) "video/mp4" else "image/jpeg"
            val name = "TGlass_${System.currentTimeMillis()}.$ext"
            val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/TGlass")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return@runCatching false
                try {
                    val out = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                    out.use { o -> src.inputStream().use { it.copyTo(o) } }
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(folder), "TGlass").apply { mkdirs() }
                val out = File(dir, name)
                src.copyTo(out, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(mime), null)
                true
            }
        }.getOrDefault(false)
    }

    /** Opens the SMS app with an invite to Telegram for [phone]. */
    fun smsInvite(context: Context, phone: String): Boolean {
        val number = phone.filter { it.isDigit() || it == '+' }
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", InviteText)
        return start(context, sms) || shareText(context, InviteText)
    }

    /**
     * Opens a user in the official Telegram app (tg:// links), falling back to the t.me web link.
     * Returns false if nothing could handle it.
     */
    fun openInTelegram(context: Context, username: String?, userId: Long): Boolean {
        val links = buildList {
            if (!username.isNullOrBlank()) add("tg://resolve?domain=$username")
            if (userId > 0) add("tg://user?id=$userId")
            if (!username.isNullOrBlank()) add("https://t.me/$username")
        }
        return links.any { start(context, Intent(Intent.ACTION_VIEW, Uri.parse(it))) } || openTelegramApp(context)
    }

    /** Launches an installed Telegram app. */
    fun openTelegramApp(context: Context): Boolean {
        val launch = TelegramPackages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        return launch != null && start(context, launch)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
