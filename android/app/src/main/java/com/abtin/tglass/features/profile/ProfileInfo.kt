package com.abtin.tglass.features.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.ProfileMusic
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.formatDuration
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

internal fun copyText(context: Context, label: String, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

/** Rounded translucent card that holds info rows (phone, username, location, birthday, bio…). */
@Composable
internal fun InfoCard(palette: ProfilePalette, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedRectangle(26.dp))
            .background(palette.card),
        content = content,
    )
}

/**
 * One info row: small [label] over the [value]. [trailing] sits on the right (QR icon, mini map).
 * [userText] values (bio, description, names) are laid out by their own direction so Persian/Arabic reads RTL.
 */
@Composable
internal fun InfoRow(
    label: String,
    value: String,
    palette: ProfilePalette,
    divider: Boolean,
    accent: Boolean = false,
    userText: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** Bio / description: links, @usernames, #hashtags, emails and numbers are blue and tappable; long press copies. */
    linkify: Boolean = false,
    entities: List<com.abtin.tglass.data.Entity> = emptyList(),
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (!linkify && (onClick != null || onLongClick != null)) Modifier.iosClickable(onLongClick = onLongClick, onClick = onClick ?: {}) else Modifier)
                .heightIn(min = 58.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T(label, TgTheme.type.subheadline, palette.cardLabel, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(4.dp))
                    }
                    val style = if (userText) TgTheme.type.body.copy(textDirection = TextDirection.Content) else TgTheme.type.body
                    if (linkify) {
                        com.abtin.tglass.ui.components.LinkifiedText(value, style, palette.cardText, palette.cardAccent, entities = entities)
                    } else {
                        T(value, style, if (accent) palette.cardAccent else palette.cardText)
                    }
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        if (divider) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(palette.divider))
        }
    }
}

/** Small QR button at the end of the username / link row. */
@Composable
internal fun QrButton(palette: ProfilePalette, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(Capsule()).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(IosIcons.QrCode, palette.cardAccent, 22.dp) }
}

/** The little map tile with a red pin shown next to a location (a stylized map; no map SDK in the app). */
@Composable
internal fun MiniMap() {
    Canvas(Modifier.size(44.dp).clip(RoundedRectangle(10.dp))) {
        drawRect(Color(0xFFE9F0DA))
        val road = Color.White
        drawLine(road, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.38f), strokeWidth = 4f)
        drawLine(road, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.52f, size.height), strokeWidth = 3f)
        drawRect(Color(0xFFCFE3B6), Offset(size.width * 0.62f, size.height * 0.62f), Size(size.width * 0.3f, size.height * 0.28f))
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        val r = size.minDimension * 0.17f
        val pin = Path().apply {
            moveTo(cx, cy + r * 2.1f)
            lineTo(cx - r * 0.75f, cy + r * 0.6f)
            lineTo(cx + r * 0.75f, cy + r * 0.6f)
            close()
        }
        drawPath(pin, Color(0xFFE5484D))
        drawCircle(Color(0xFFE5484D), r, Offset(cx, cy))
        drawCircle(Color.White, r * 0.38f, Offset(cx, cy))
    }
}

/** Personal channel card ("Cool Things · last post") above the info card. */
@Composable
internal fun ChannelCard(title: String, subtitle: String, chatId: Long, palette: ProfilePalette, onClick: () -> Unit) {
    InfoCard(palette) {
        Row(
            Modifier.fillMaxWidth().iosClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(title, chatId, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                com.abtin.tglass.core.emoji.EmojiText(title, TgTheme.type.headline.copy(textDirection = TextDirection.Content), palette.cardText, maxLines = 1)
                T(subtitle, TgTheme.type.subheadline.copy(textDirection = TextDirection.Content), palette.cardLabel, maxLines = 1)
            }
            Icon(IosIcons.ChevronRight, palette.cardLabel, 14.dp)
        }
    }
}

/** "♫ Dance of the Knights · Symphony Orchestra ›" under the buttons; plays the song. */
@Composable
internal fun MusicLine(music: ProfileMusic, playing: Boolean, palette: ProfilePalette, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .clip(Capsule())
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(if (playing) IosIcons.Pause else Icons.Rounded.MusicNote, palette.title, 16.dp)
        Spacer(Modifier.width(5.dp))
        T(music.title, TgTheme.type.footnote, palette.title, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (music.performer.isNotBlank()) {
            T(" · ${music.performer}", TgTheme.type.footnote, palette.subtitle, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        }
        if (music.duration > 0) T("  ${formatDuration(music.duration)}", TgTheme.type.footnote, palette.subtitle, maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Icon(IosIcons.ChevronRight, palette.subtitle, 11.dp)
    }
}

/** Full-screen QR code of a t.me link, like Telegram's "QR Code" sheet. */
@Composable
internal fun QrOverlay(link: String, title: String, seed: Long, onShare: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val matrix = remember(link) { com.abtin.tglass.features.settings.QrCode.encode(link) }
    val (a, b) = com.abtin.tglass.ui.components.avatarColors(seed)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(a, b)))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .clip(RoundedRectangle(30.dp))
                .background(Color.White)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (matrix != null) {
                Canvas(Modifier.size(220.dp)) {
                    val n = matrix.size
                    val cell = size.minDimension / n
                    for (y in 0 until n) for (x in 0 until n) {
                        if (matrix[y][x]) drawRect(b, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            T(title, TgTheme.type.title3, b, weight = FontWeight.Bold, maxLines = 1, align = TextAlign.Center)
            T(link.removePrefix("https://"), TgTheme.type.subheadline, Color(0xFF8E8E93), maxLines = 1)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QrAction("Copy Link", b, onCopy)
                QrAction("Share", b, onShare)
            }
        }
    }
}

@Composable
private fun QrAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(Capsule()).background(color).clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) { T(text, TgTheme.type.subheadline, Color.White, weight = FontWeight.SemiBold) }
}

/** Opens Android's share sheet with [text]. */
internal fun shareText(context: Context, text: String) {
    runCatching {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
        context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
internal fun rememberContext(): Context = LocalContext.current
