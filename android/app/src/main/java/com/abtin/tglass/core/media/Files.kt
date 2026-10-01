package com.abtin.tglass.core.media

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.MessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

object Files {
    /** Copies picked documents into the cache (keeping their names) so they can be sent. */
    suspend fun prepareDocuments(context: Context, uris: List<Uri>): List<MessageContent.File> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            runCatching {
                val resolver = context.contentResolver
                var name = "file"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { name = it }
                }
                val dir = File(context.cacheDir, "upload/${System.nanoTime()}").apply { mkdirs() }
                val out = File(dir, name.replace('/', '_'))
                resolver.openInputStream(uri)?.use { input -> FileOutputStream(out).use { input.copyTo(it) } } ?: return@runCatching null
                val mime = resolver.getType(uri)
                MessageContent.File(
                    name = out.name,
                    size = sizeText(out.length()),
                    file = ImageRef(0, out.absolutePath),
                    mime = mime,
                    music = mime?.startsWith("audio/") == true,
                )
            }.getOrNull()
        }
    }

    fun sizeText(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(Locale.US, bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(Locale.US, bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%d KB".format(Locale.US, bytes shr 10)
        else -> "$bytes B"
    }

    /** Opens a local file in another app (PDF viewer, player…). Returns false if nothing can open it. */
    fun open(context: Context, path: String, mime: String?): Boolean {
        val file = File(path)
        val type = mime?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.US))
            ?: "*/*"
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Best current location: a fresh fix if one arrives within a few seconds, else the last known one. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            val fresh = withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    lm.getCurrentLocation(providers.first(), null, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                }
            }
            if (fresh != null) return fresh
        }
        return last
    }
}
