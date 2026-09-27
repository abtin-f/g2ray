package com.abtin.tglass.features.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.AutoDownload
import com.abtin.tglass.data.DataUsageRow
import com.abtin.tglass.data.DownloadNetwork
import com.abtin.tglass.data.formatBytes
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.IOSSwitch
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SegmentedControl
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.kyant.shapes.Capsule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Automatic media download settings per network, kept on the device (and sent to Telegram by the live repository). */
object AutoDownloadPrefs {
    private var prefs: SharedPreferences? = null
    private val values = mutableStateMapOf<DownloadNetwork, AutoDownload>()

    private fun default(n: DownloadNetwork) = when (n) {
        DownloadNetwork.Cellular -> AutoDownload(enabled = true, photos = true, videos = false, files = false)
        DownloadNetwork.WiFi -> AutoDownload(enabled = true, photos = true, videos = true, files = true)
        DownloadNetwork.Roaming -> AutoDownload(enabled = false, photos = true, videos = false, files = false)
    }

    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("tglass_autodownload", Context.MODE_PRIVATE)
        prefs = p
        for (n in DownloadNetwork.entries) {
            val d = default(n)
            val k = n.name
            values[n] = AutoDownload(
                enabled = p.getBoolean("${k}_on", d.enabled),
                photos = p.getBoolean("${k}_photos", d.photos),
                videos = p.getBoolean("${k}_videos", d.videos),
                files = p.getBoolean("${k}_files", d.files),
            )
        }
    }

    fun get(n: DownloadNetwork): AutoDownload = values[n] ?: default(n)

    fun set(n: DownloadNetwork, v: AutoDownload) {
        values[n] = v
        val k = n.name
        prefs?.edit()
            ?.putBoolean("${k}_on", v.enabled)
            ?.putBoolean("${k}_photos", v.photos)
            ?.putBoolean("${k}_videos", v.videos)
            ?.putBoolean("${k}_files", v.files)
            ?.apply()
    }

    fun summary(n: DownloadNetwork): String {
        val v = get(n)
        if (!v.enabled || (!v.photos && !v.videos && !v.files)) return "Disabled"
        return listOfNotNull("Photos".takeIf { v.photos }, "Videos".takeIf { v.videos }, "Files".takeIf { v.files }).joinToString(", ")
    }
}

private val StorageColors = listOf(Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500))

// ---- Storage Usage ----

internal fun LazyListScope.storageUsage() {
    item {
        val repo = LocalRepository.current
        var clearing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { repo.loadStorage() }
        val info = repo.storageInfo
        if (info == null) {
            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { ActivityIndicator(26.dp) }
        } else {
            StorageContent(info, clearing, { clearing = it })
        }
    }
}

@Composable
private fun StorageContent(info: com.abtin.tglass.data.StorageInfo, clearing: Boolean, setClearing: (Boolean) -> Unit) {
        val repo = LocalRepository.current
        val c = TgTheme.colors
        val sheet = LocalActionSheet.current
        val toast = LocalToast.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(formatBytes(info.total), TgTheme.type.largeTitle, c.text, weight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            T("Telegram uses this much of your device storage.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            val parts = listOf(info.filesSize, info.databaseSize, info.otherSize)
            val total = parts.sum().coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(Capsule()).background(c.searchField)) {
                parts.forEachIndexed { i, size ->
                    val w = size.toFloat() / total
                    if (w > 0.005f) Box(Modifier.weight(w).fillMaxHeight().background(StorageColors[i]))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Section(footer = "Media and files stay in the Telegram cloud, so you can download them again when you need them.") {
            UsageCell("Media and Files", "${info.fileCount} files", formatBytes(info.filesSize), StorageColors[0])
            UsageCell("Database", "Messages and chats", formatBytes(info.databaseSize), StorageColors[1])
            UsageCell("Other", "Language packs and logs", formatBytes(info.otherSize), StorageColors[2], divider = false)
        }
        Spacer(Modifier.height(24.dp))
        Section(footer = "Profile photos, stickers and wallpapers are kept.") {
            Cell(
                if (clearing) "Clearing…" else "Clear Cache (${formatBytes(info.filesSize)})",
                titleColor = if (clearing || info.filesSize == 0L) c.secondaryText else c.accent,
                chevron = false,
                divider = false,
                onClick = if (clearing || info.filesSize == 0L) null else ({
                    sheet.show(SheetRequest(
                        title = "Clear Cache?",
                        message = "All cached media will be deleted from this device. You can download it again from the cloud.",
                        alert = true,
                        actions = listOf(SheetAction("Clear", destructive = true) {
                            setClearing(true)
                            repo.clearCache { freed, err ->
                                setClearing(false)
                                if (err != null) toast.show(err, Icons.Rounded.ErrorOutline)
                                else toast.show(if (freed != null) "${formatBytes(freed)} freed" else "Cache cleared")
                            }
                        }),
                    ))
                }),
            )
        }
}

@Composable
private fun UsageCell(title: String, subtitle: String?, value: String, color: Color, divider: Boolean = true) {
    Cell(
        title,
        subtitle = subtitle,
        value = value,
        chevron = false,
        divider = divider,
        leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
    )
}

// ---- Data Usage ----

private val sinceFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

internal fun LazyListScope.networkUsage() {
    item {
        val repo = LocalRepository.current
        var tab by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { repo.loadDataUsage() }
        val usage = repo.dataUsage
        SegmentedControl(listOf("All", "Mobile", "Wi-Fi", "Roaming"), tab, { tab = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(20.dp))
        if (usage == null) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { ActivityIndicator(26.dp) }
        } else {
            NetworkUsageContent(usage, tab)
        }
    }
}

@Composable
private fun NetworkUsageContent(usage: com.abtin.tglass.data.DataUsage, tab: Int) {
        val repo = LocalRepository.current
        val c = TgTheme.colors
        val sheet = LocalActionSheet.current
        val rows: List<DataUsageRow> = when (tab) {
            1 -> usage.mobile
            2 -> usage.wifi
            3 -> usage.roaming
            else -> (usage.mobile + usage.wifi + usage.roaming)
                .groupBy { it.title }
                .map { (title, list) -> DataUsageRow(title, list.sumOf { it.sent }, list.sumOf { it.received }) }
                .sortedByDescending { it.sent + it.received }
        }
        val sent = rows.sumOf { it.sent }
        val received = rows.sumOf { it.received }
        Section(header = "Total") {
            Cell("Bytes Sent", value = formatBytes(sent), chevron = false)
            Cell("Bytes Received", value = formatBytes(received), chevron = false, divider = false)
        }
        Spacer(Modifier.height(24.dp))
        if (rows.isNotEmpty()) {
            Section(header = "Details") {
                rows.forEachIndexed { i, r ->
                    Cell(r.title, subtitle = "Sent ${formatBytes(r.sent)} • Received ${formatBytes(r.received)}", value = formatBytes(r.sent + r.received), chevron = false, divider = i != rows.lastIndex)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        val since = if (usage.sinceMs > 0) sinceFormat.format(Instant.ofEpochMilli(usage.sinceMs).atZone(ZoneId.systemDefault())) else null
        Section(footer = since?.let { "Network usage since $it." }) {
            Cell("Reset Statistics", titleColor = c.destructive, chevron = false, divider = false, onClick = {
                sheet.show(SheetRequest(
                    title = "Reset Statistics?",
                    alert = true,
                    actions = listOf(SheetAction("Reset", destructive = true) { repo.resetDataUsage() }),
                ))
            })
        }
}

// ---- Automatic media download ----

internal fun LazyListScope.autoDownload(network: DownloadNetwork) {
    item {
        val repo = LocalRepository.current
        val context = LocalContext.current
        AutoDownloadPrefs.attach(context)
        val v = AutoDownloadPrefs.get(network)
        fun update(n: AutoDownload) {
            AutoDownloadPrefs.set(network, n)
            repo.applyAutoDownload(network, n)
        }
        val where = when (network) {
            DownloadNetwork.Cellular -> "using mobile data"
            DownloadNetwork.WiFi -> "connected to Wi-Fi"
            DownloadNetwork.Roaming -> "roaming"
        }
        Section(footer = "Media will be downloaded automatically when you are $where.") {
            Cell("Auto-Download Media", chevron = false, divider = false, trailing = { IOSSwitch(v.enabled, { update(v.copy(enabled = it)) }) })
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Types of Media") {
            Cell("Photos", chevron = false, titleColor = if (v.enabled) TgTheme.colors.text else TgTheme.colors.secondaryText, trailing = { IOSSwitch(v.photos, { update(v.copy(photos = it)) }) })
            Cell("Videos", chevron = false, titleColor = if (v.enabled) TgTheme.colors.text else TgTheme.colors.secondaryText, trailing = { IOSSwitch(v.videos, { update(v.copy(videos = it)) }) })
            Cell("Files", chevron = false, divider = false, titleColor = if (v.enabled) TgTheme.colors.text else TgTheme.colors.secondaryText, trailing = { IOSSwitch(v.files, { update(v.copy(files = it)) }) })
        }
    }
}

/** Page shown for each network in Data and Storage → Automatic Media Download. */
internal fun Page.downloadNetwork(): DownloadNetwork? = when (this) {
    Page.AutoDownloadCellular -> DownloadNetwork.Cellular
    Page.AutoDownloadWifi -> DownloadNetwork.WiFi
    Page.AutoDownloadRoaming -> DownloadNetwork.Roaming
    else -> null
}
