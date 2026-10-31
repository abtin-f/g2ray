package com.abtin.tglass.core.emoji

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** An emoji found in a text: chars [start, end) are drawn with the Apple image [name] (`1f600`, `2764-fe0f`, …). */
class EmojiMatch(val start: Int, val end: Int, val name: String)

/**
 * Apple (iOS) emoji images, like Telegram for iPhone.
 *
 * Apple's emoji artwork is Apple's copyright, so **no image ships with the app**: each emoji is downloaded on first
 * use from the npm package `emoji-datasource-apple` (served by jsDelivr, unpkg as a mirror), stored as a 64 px PNG
 * under `filesDir/apple_emoji_<version>/<name>.png`, and decoded into a small in-memory LRU. Until an image is on
 * disk (or when offline, or when the user picks the System style) the system emoji glyph is shown instead.
 *
 * The only bundled data is `assets/apple_emoji_names.txt`: the file names of that package version (`unified`
 * code points, lowercase, dash separated — the MIT-licensed emoji-data index, no artwork), used to recognise emoji
 * sequences in text, including skin tones, ZWJ sequences, keycaps and flags.
 */
object AppleEmoji {
    /** Pinned `emoji-datasource-apple` version (Emoji 16.0 / macOS 15.5 artwork). */
    const val DataVersion = "16.0.0"
    private val mirrors = listOf(
        "https://cdn.jsdelivr.net/npm/emoji-datasource-apple@$DataVersion/img/apple/64/",
        "https://unpkg.com/emoji-datasource-apple@$DataVersion/img/apple/64/",
    )
    private const val Prefs = "tglass_emoji"
    private const val KeyStyle = "style"
    private const val Asset = "apple_emoji_names.txt"

    private const val VS16 = 0xFE0F
    private const val VS15 = 0xFE0E
    private const val Keycap = 0x20E3

    private class Table(names: List<String>) {
        val exact = HashSet<String>(names.size * 2)
        /** Name by its code points without FE0F (text often drops or adds the variation selector). */
        val byKey = HashMap<String, String>(names.size * 2)
        val starters = HashSet<Int>()
        var maxLen = 1

        init {
            for (n in names) {
                val parts = n.split('-')
                exact.add(n)
                val key = parts.filter { it != "fe0f" }.joinToString("-")
                if (!byKey.containsKey(key)) byKey[key] = n
                parts.firstOrNull()?.toIntOrNull(16)?.let { starters.add(it) }
                if (parts.size > maxLen) maxLen = parts.size
            }
        }
    }

    @Volatile private var table: Table? = null
    @Volatile private var dir: File? = null
    private val started = AtomicBoolean(false)
    private val onDisk: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failedAt = ConcurrentHashMap<String, Long>()
    @Volatile private var offlineUntil = 0L
    private val readyStates = ConcurrentHashMap<String, MutableState<Boolean>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val network = Dispatchers.IO.limitedParallelism(6)
    private val main = Handler(Looper.getMainLooper())
    private val bitmaps = LruCache<String, ImageBitmap>(700) // 64×64 RGBA = 16 KB each → ~11 MB max

    /** The user's choice (Settings → Appearance → Emoji Style): Apple (default) or the system font. */
    var appleStyle by mutableStateOf(true)
        private set

    /** Bumped (debounced) whenever new images land on disk, so texts re-annotate. */
    var revision by mutableIntStateOf(0)
        private set

    private var loaded by mutableStateOf(false)

    /** Apple emoji are on and the emoji index is loaded (a Compose state read). */
    val active: Boolean get() = appleStyle && loaded

    /** Loads the index and the list of cached images in the background. Cheap to call repeatedly. */
    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            val style = runCatching { app.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(KeyStyle, "apple") }.getOrNull()
            val d = File(app.filesDir, "apple_emoji_$DataVersion")
            runCatching { d.mkdirs() }
            dir = d
            d.list()?.forEach { if (it.endsWith(".png")) onDisk.add(it.removeSuffix(".png")) }
            val names = runCatching {
                app.assets.open(Asset).bufferedReader().useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
            }.getOrDefault(emptyList())
            if (names.isNotEmpty()) table = Table(names)
            main.post {
                appleStyle = style != "system"
                loaded = table != null
                revision++
            }
        }
    }

    fun setAppleStyle(context: Context, apple: Boolean) {
        appleStyle = apple
        runCatching {
            context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
                .putString(KeyStyle, if (apple) "apple" else "system").apply()
        }
    }

    private fun hex(cp: Int): String {
        val h = Integer.toHexString(cp)
        return if (h.length >= 4) h else "0".repeat(4 - h.length) + h
    }

    /** Emoji sequences in [text], longest match first, in order. Empty until the index is loaded. */
    fun find(text: String): List<EmojiMatch> {
        val t = table ?: return emptyList()
        if (text.isEmpty()) return emptyList()
        val count = text.codePointCount(0, text.length)
        val cps = IntArray(count)
        val offs = IntArray(count + 1)
        var i = 0
        var k = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            cps[k] = cp
            offs[k] = i
            i += Character.charCount(cp)
            k++
        }
        offs[count] = text.length
        var out: ArrayList<EmojiMatch>? = null
        k = 0
        val exactSb = StringBuilder()
        val keySb = StringBuilder()
        while (k < count) {
            val cp = cps[k]
            if (cp !in t.starters) { k++; continue }
            // ASCII / Latin starters (#, *, 0-9, ©, ®) are only emoji with FE0F or a keycap after them.
            if (cp < 0x2000 && (k + 1 >= count || (cps[k + 1] != VS16 && cps[k + 1] != Keycap))) { k++; continue }
            var matched = 0
            var name: String? = null
            var len = minOf(t.maxLen + 1, count - k)
            while (len >= 1) {
                // A window must not end inside a ZWJ join.
                if (cps[k + len - 1] == 0x200D) { len--; continue }
                exactSb.setLength(0)
                keySb.setLength(0)
                var hasVs = false
                for (j in k until k + len) {
                    val h = hex(cps[j])
                    if (exactSb.isNotEmpty()) exactSb.append('-')
                    exactSb.append(h)
                    if (cps[j] == VS16) { hasVs = true; continue }
                    if (keySb.isNotEmpty()) keySb.append('-')
                    keySb.append(h)
                }
                val exact = exactSb.toString()
                val n = if (exact in t.exact) exact else t.byKey[keySb.toString()]
                if (n != null) {
                    val next = if (k + len < count) cps[k + len] else -1
                    val single = n.indexOf('-').let { d -> d > 0 && n.endsWith("-fe0f") && n.indexOf('-', d + 1) < 0 }
                    val textStyle = next == VS15 || (single && !hasVs && cp < 0x2600)
                    if (!textStyle) {
                        name = n
                        matched = len
                        break
                    }
                }
                len--
            }
            if (name != null) {
                // Swallow a trailing FE0F that wasn't part of the window.
                var end = k + matched
                if (end < count && cps[end] == VS16) end++
                (out ?: ArrayList<EmojiMatch>().also { out = it }).add(EmojiMatch(offs[k], offs[end], name))
                k = end
            } else k++
        }
        return out ?: emptyList()
    }

    /** The image name when [emoji] is exactly one emoji (e.g. a panel cell or a reaction), else null. */
    fun single(emoji: String): String? {
        val trimmed = emoji.trim()
        val m = find(trimmed)
        return if (m.size == 1 && m[0].start == 0 && m[0].end == trimmed.length) m[0].name else null
    }

    fun isOnDisk(name: String): Boolean = name in onDisk

    /** Observable "the image is on disk" flag for one emoji. */
    fun readyState(name: String): State<Boolean> =
        readyStates[name] ?: mutableStateOf(name in onDisk).also { readyStates.putIfAbsent(name, it) }

    fun cachedBitmap(name: String): ImageBitmap? = bitmaps.get(name)

    /** Decodes a cached image (call off the main thread when many are needed at once). */
    fun decode(name: String): ImageBitmap? {
        bitmaps.get(name)?.let { return it }
        val d = dir ?: return null
        val f = File(d, "$name.png")
        if (!f.exists()) return null
        val bmp = runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        if (bmp == null) {
            // Broken file: forget it so it downloads again.
            runCatching { f.delete() }
            onDisk.remove(name)
            return null
        }
        bitmaps.put(name, bmp)
        return bmp
    }

    /** Starts downloading an emoji image (no-op when cached, in flight, recently failed or offline). */
    fun request(name: String) {
        if (name in onDisk) return
        val d = dir ?: return
        val now = SystemClock.elapsedRealtime()
        if (now < offlineUntil) return
        failedAt[name]?.let { if (now - it < 60_000) return }
        if (!inFlight.add(name)) return
        scope.launch(network) {
            val ok = download(name, d)
            inFlight.remove(name)
            if (ok) {
                onDisk.add(name)
                failedAt.remove(name)
                main.post {
                    readyStates[name]?.value = true
                    bumpRevision()
                }
            } else {
                failedAt[name] = SystemClock.elapsedRealtime()
            }
        }
    }

    private var bumpPosted = false // main thread only

    private fun bumpRevision() {
        if (bumpPosted) return
        bumpPosted = true
        main.postDelayed({
            bumpPosted = false
            revision++
        }, 150)
    }

    private fun download(name: String, d: File): Boolean {
        var networkError = false
        for (base in mirrors) {
            var c: HttpURLConnection? = null
            try {
                c = URL("$base$name.png").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.instanceFollowRedirects = true
                if (c.responseCode != HttpURLConnection.HTTP_OK) continue
                val tmp = File(d, "$name.png.part")
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.path, bounds)
                if (bounds.outWidth <= 0) {
                    tmp.delete()
                    continue
                }
                val target = File(d, "$name.png")
                if (tmp.renameTo(target)) return true
                tmp.delete()
            } catch (e: java.io.IOException) {
                networkError = true
            } catch (e: Exception) {
            } finally {
                runCatching { c?.disconnect() }
            }
        }
        // No connection at all: don't hammer the network for a while (the system glyph stays).
        if (networkError) offlineUntil = SystemClock.elapsedRealtime() + 20_000
        return false
    }
}
