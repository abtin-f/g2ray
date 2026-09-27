package com.abtin.tglass.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.T
import com.kyant.shapes.RoundedRectangle

/** Settings → QR code button: the user's t.me link as a QR code, with Copy and Share. */
internal fun LazyListScope.myQrCode() {
    item { MyQrCode() }
}

@Composable
private fun MyQrCode() {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val context = LocalContext.current
    val toast = LocalToast.current
    val me = repo.me
    var link by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(me.username) {
        repo.loadMyLink { link = it; loaded = true }
    }
    val matrix = remember(link) { link?.let { QrCode.encode(it) } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .clip(RoundedRectangle(32.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(me.name, 3, 64.dp, photoPeer = me.id)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                when {
                    matrix != null -> QrImage(matrix, Modifier.size(232.dp))
                    !loaded -> ActivityIndicator(26.dp, Color(0xFF8E8E93))
                    else -> T("No public link", TgTheme.type.subheadline, Color(0xFF8E8E93), align = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(14.dp))
            T(
                me.username?.let { "@${it.uppercase()}" } ?: me.name,
                TgTheme.type.headline, Color(0xFF0088FF), weight = FontWeight.Bold, align = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
        T(
            if (me.username != null) "Scan this code with a phone camera to open your profile in Telegram."
            else "You don't have a username, so this link expires after a while. Set a username in My Profile to get a permanent link.",
            TgTheme.type.footnote, c.secondaryText, align = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
    }
    val current = link
    if (current != null) {
        Section(footer = current) {
            Cell("Copy Link", titleColor = c.accent, chevron = false, onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("link", current))
                toast.show("Link copied")
            })
            Cell("Share Link", titleColor = c.accent, chevron = false, divider = false, onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, current)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            })
        }
    }
}

/** Draws a QR module matrix (dark modules in black; the caller provides the light quiet zone). */
@Composable
private fun QrImage(matrix: Array<BooleanArray>, modifier: Modifier) {
    Canvas(modifier) {
        val n = matrix.size
        val cell = size.minDimension / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix[y][x]) {
                    // Slight overlap avoids hairline gaps between neighbouring modules.
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}
