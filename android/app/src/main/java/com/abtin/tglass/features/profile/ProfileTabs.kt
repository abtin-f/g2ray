package com.abtin.tglass.features.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.ProfileGift
import com.abtin.tglass.data.Story
import com.abtin.tglass.data.senderName
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.formatDuration
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** Tabs of the profile page, in Telegram's order. */
internal enum class ProfileTab(val title: String) {
    Posts("Posts"), Members("Members"), Gifts("Gifts"), Media("Media"), Files("Files"), Links("Links"),
    Music("Music"), Voice("Voice"), Gifs("GIFs"), Groups("Groups"),
}

/**
 * The tab switcher: labels on the page with a liquid-glass pill that slides (and stretches while moving) to
 * the selected tab and follows the finger while the content is swiped. When [pinned] under the top bar,
 * a glass capsule appears behind the labels. Lives outside the list's backdrop layer.
 */
@Composable
internal fun ProfileTabBar(
    tabs: List<ProfileTab>,
    selected: Int,
    /** Pill position as a fractional tab index (animated by the screen; follows the finger while swiping). */
    position: () -> Float,
    palette: ProfilePalette,
    pinned: Boolean,
    giftIcons: List<ProfileGift>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val xs = remember(tabs) { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }
    val ws = remember(tabs) { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }
    var viewport by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val pinnedAlpha by animateFloatAsState(if (pinned) 1f else 0f, label = "tabPinned")
    LaunchedEffect(selected, viewport, ws.toList()) {
        val x = xs.getOrNull(selected) ?: return@LaunchedEffect
        val w = ws.getOrNull(selected) ?: return@LaunchedEffect
        if (viewport > 0 && w > 0f) scroll.animateScrollTo((x - (viewport - w) / 2f).toInt().coerceAtLeast(0))
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp).height(44.dp).onSizeChanged { viewport = it.width }) {
        if (pinnedAlpha > 0.01f) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = pinnedAlpha }.profileGlass(Capsule(), palette.button, palette.buttonSolid))
        }
        Row(Modifier.fillMaxSize().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
          // At least as wide as the bar with the tabs centered: a few tabs (e.g. only Media and Files) sit in
          // the middle like on iOS; when they don't fit this is just their width and the row scrolls.
          Box(Modifier.widthIn(min = with(density) { viewport.toDp() }), contentAlignment = Alignment.Center) {
            Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                // Pill position: between the two tabs around the (animated, finger-driven) index.
                val pos = position().coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                val i0 = floor(pos).toInt().coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                val i1 = (i0 + 1).coerceAtMost(tabs.size - 1)
                val f = pos - i0
                val px = (xs.getOrNull(i0) ?: 0f) * (1 - f) + (xs.getOrNull(i1) ?: 0f) * f
                val pw = (ws.getOrNull(i0) ?: 0f) * (1 - f) + (ws.getOrNull(i1) ?: 0f) * f
                // Liquid stretch: strongest halfway between two tabs.
                val stretch = sin(f * PI).toFloat()
                if (pw > 0f) {
                    Box(
                        Modifier
                            .offset { IntOffset(px.toInt(), 0) }
                            .width(with(density) { pw.toDp() })
                            .height(36.dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.16f * stretch
                                scaleY = 1f - 0.08f * stretch
                            }
                            .profileGlass(Capsule(), palette.pill, palette.pillSolid)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { i, tab ->
                        val sel = i == selected
                        Row(
                            Modifier
                                .height(36.dp)
                                .onPlaced { xs[i] = it.positionInParent().x; ws[i] = it.size.width.toFloat() }
                                .clip(Capsule())
                                .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(tab.title, TgTheme.type.subheadline, if (sel) palette.tabSelected else palette.tab, weight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                            if (tab == ProfileTab.Gifts && giftIcons.isNotEmpty()) {
                                Spacer(Modifier.width(3.dp))
                                giftIcons.take(3).forEach { g -> GiftGlyph(g, 18.dp) }
                            }
                        }
                    }
                }
            }
          }
        }
    }
}

/** Placeholder text of an empty tab. */
internal fun LazyListScope.emptyTab(key: String, title: String, palette: ProfilePalette, itemModifier: Modifier) {
    item(key = key) {
        Column(itemModifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("No ${title.lowercase()} yet", TgTheme.type.headline, palette.title, align = TextAlign.Center)
            T("Shared ${title.lowercase()} will appear here.", TgTheme.type.subheadline, palette.subtitle, align = TextAlign.Center)
        }
    }
}

/** Posts tab: 3-column grid of story covers (pinned ones first, with a pin), like Telegram's profile stories. */
internal fun LazyListScope.postsGrid(posts: List<Story>, itemModifier: Modifier, onOpen: (Int) -> Unit) {
    posts.chunked(3).forEachIndexed { r, row ->
        item(key = "posts-row$r") {
            Row(itemModifier.fillMaxWidth().padding(bottom = 1.5.dp), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                row.forEachIndexed { i, s -> PostTile(s, Modifier.weight(1f)) { onOpen(r * 3 + i) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PostTile(s: Story, modifier: Modifier, onClick: () -> Unit) {
    val colors = s.colors.map { Color(it) }
    val background = Brush.linearGradient(if (colors.size >= 2) colors else listOf(Color(0xFF48484A), Color(0xFF1C1C1E)))
    val image = s.image
    Box(modifier.aspectRatio(0.75f).background(background).bounceClickable(onClick), contentAlignment = Alignment.Center) {
        when {
            image != null -> TgImage(image, Modifier.matchParentSize(), maxPx = 480, contentScale = ContentScale.Crop)
            s.emoji.isNotEmpty() -> T(s.emoji, TgTheme.type.body.copy(fontSize = 38.sp, lineHeight = 44.sp))
            !s.loaded -> ActivityIndicator(18.dp, Color.White)
        }
        if (s.pinned) {
            Box(
                Modifier.align(Alignment.TopStart).padding(5.dp).size(20.dp).clip(Capsule()).background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) { Icon(TgIcons.MsgPinned, Color.White, 13.dp) }
        }
        if (s.video != null && s.durationSec > 0) {
            T(
                formatDuration(s.durationSec.toInt()), TgTheme.type.caption2, Color.White, weight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(Capsule()).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** 3-column grid of gift tiles with radial color backgrounds and "1 of N" corner ribbons. */
internal fun LazyListScope.giftGrid(gifts: List<ProfileGift>, itemModifier: Modifier, onOpen: (ProfileGift) -> Unit) {
    gifts.chunked(3).forEachIndexed { r, row ->
        item(key = "gifts-row$r") {
            Row(
                itemModifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { g -> GiftTile(g, Modifier.weight(1f)) { onOpen(g) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GiftTile(g: ProfileGift, modifier: Modifier, onClick: () -> Unit) {
    val center = g.centerColor?.let { Color(it or 0xFF000000.toInt()) } ?: Color(0xFF8FA8D8)
    val edge = g.edgeColor?.let { Color(it or 0xFF000000.toInt()) } ?: Color(0xFF5A76B0)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedRectangle(18.dp))
            .background(Brush.radialGradient(listOf(center, edge)))
            .bounceClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        GiftGlyph(g, 76.dp)
        val ribbon = g.ribbon
        if (ribbon != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = 14.dp)
                    .rotate(45f)
                    .width(100.dp)
                    .background(edge.copy(alpha = 0.85f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) { T(ribbon, TgTheme.type.caption2.copy(fontSize = 9.sp, lineHeight = 11.sp), Color.White, weight = FontWeight.SemiBold, maxLines = 1) }
        } else if (g.stars > 0) {
            T(
                "⭐ ${g.stars}", TgTheme.type.caption2, Color.White, weight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    .clip(Capsule()).background(Color.Black.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

/** A row of the Files / Links / Music / Voice tabs. Files download and open on tap, audio plays inline. */
@Composable
internal fun SharedRow(m: Message, palette: ProfilePalette, divider: Boolean) {
    val repo = LocalRepository.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = LocalToast.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val player = com.abtin.tglass.core.media.VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val content = m.content
    val ref = when (content) {
        is MessageContent.File -> content.file
        is MessageContent.Voice -> content.media
        is MessageContent.VideoNote -> content.video
        else -> null
    }
    val path = ref?.let { repo.filePath(it) }
    var pending by remember(key) { mutableStateOf(false) }
    fun act(p: String) {
        when (content) {
            is MessageContent.File -> if (content.music) player.toggle(context, key, p, content.duration)
                else if (!com.abtin.tglass.core.media.Files.open(context, p, content.mime)) toast.error("No app can open this file")
            is MessageContent.Voice -> player.toggle(context, key, p, content.seconds)
            is MessageContent.VideoNote -> player.toggle(context, key, p, content.seconds)
            else -> {}
        }
    }
    LaunchedEffect(pending, path) { if (pending && path != null) { pending = false; act(path) } }
    val (title, subtitle) = when (content) {
        is MessageContent.File -> content.name to listOfNotNull(content.performer, content.size).joinToString(" · ")
        is MessageContent.Voice -> repo.senderName(m) to formatDuration(content.seconds)
        is MessageContent.VideoNote -> repo.senderName(m) to "Video message · ${formatDuration(content.seconds)}"
        is MessageContent.Link -> content.title.ifBlank { content.site } to content.text
        else -> m.preview to ""
    }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .iosClickable(onClick = {
                    when {
                        content is MessageContent.Link -> {
                            val url = com.abtin.tglass.features.chat.detectEntities(content.text).firstOrNull { it.type == EntityType.Url }
                                ?.let { content.text.substring(it.start, it.end) }
                            if (url != null) runCatching { uri.openUri(if (url.startsWith("http")) url else "https://$url") }
                        }
                        ref == null -> {}
                        path != null -> act(path)
                        else -> { pending = true; repo.requestImage(ref) }
                    }
                })
                .heightIn(min = 58.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedRectangle(10.dp)).background(palette.cardAccent.copy(alpha = if (palette.cardAccent == Color.White) 0.22f else 1f)), contentAlignment = Alignment.Center) {
                val playing = player.currentKey == key && player.playing
                when {
                    pending && path == null -> ActivityIndicator(18.dp, Color.White)
                    content is MessageContent.Voice || content is MessageContent.VideoNote || (content is MessageContent.File && content.music) ->
                        Icon(if (playing) IosIcons.Pause else IosIcons.Play, Color.White, 20.dp)
                    content is MessageContent.Link -> T(content.site.take(1).uppercase(), TgTheme.type.headline, Color.White)
                    else -> Icon(TgIcons.AttFile, Color.White, 22.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(title, TgTheme.type.body.copy(textDirection = TextDirection.Content), palette.cardText, maxLines = 1)
                val sub = listOf(subtitle, formatDay(m.date)).filter { it.isNotBlank() }.joinToString(" · ")
                T(sub, TgTheme.type.subheadline.copy(textDirection = TextDirection.Content), palette.cardLabel, maxLines = 2)
            }
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).padding(start = 68.dp).fillMaxWidth().height(0.5.dp).background(palette.divider))
    }
}

/** A chat row (groups in common). */
@Composable
internal fun ChatRowCell(title: String, subtitle: String?, chatId: Long, palette: ProfilePalette, divider: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().iosClickable(onClick = onClick).heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(title, chatId, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                com.abtin.tglass.core.emoji.EmojiText(title, TgTheme.type.body.copy(textDirection = TextDirection.Content), palette.cardText, maxLines = 1)
                if (subtitle != null) T(subtitle, TgTheme.type.subheadline, palette.cardLabel, maxLines = 1)
            }
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).padding(start = 66.dp).fillMaxWidth().height(0.5.dp).background(palette.divider))
    }
}
