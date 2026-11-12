package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.emoji.StickerArt
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.StickerSetInfo
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.MenuAction
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.ToastState
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The "Add Stickers" / "Add Emoji" sheet (Telegram iOS StickerPackScreen): opened from anywhere with [openSet]
 * (a sticker's set) or [openEmoji] (the pack of a premium emoji), drawn by [StickerSetSheetHost] above all screens.
 */
@Stable
object StickerSetSheet {
    internal class Request(val setId: Long, val customEmojiId: Long)

    internal var request by mutableStateOf<Request?>(null)

    /** Shows the sticker set [setId]. */
    fun openSet(setId: Long) {
        request = Request(setId, 0L)
    }

    /** Shows the emoji pack the custom emoji [customEmojiId] belongs to. */
    fun openEmoji(customEmojiId: Long) {
        if (customEmojiId != 0L) request = Request(0L, customEmojiId)
    }

    fun dismiss() {
        request = null
    }
}

/** Put once above the screens (next to the action sheet host). */
@Composable
fun StickerSetSheetHost() {
    val req = StickerSetSheet.request ?: return
    key(req) { StickerSetSheetView(req) }
}

/** Tapping a sticker message opens its set (Telegram iOS); animated emoji / dice (no set) aren't tappable. */
@Composable
internal fun Modifier.stickerSetTap(s: MessageContent.Sticker): Modifier {
    val repo = LocalRepository.current
    val source = remember { MutableInteractionSource() }
    if (s.setId == 0L && repo.isLive) return this
    return this.clickable(source, null) { StickerSetSheet.openSet(s.setId) }
}

/** Long-press menu rows for stickers and GIFs: "View Sticker Set" / "Save to GIFs". */
fun stickerGifMenuActions(repo: TelegramRepository, toast: ToastState, m: Message): List<MenuAction> {
    val out = ArrayList<MenuAction>()
    when (val c = m.content) {
        is MessageContent.Sticker -> if (c.setId != 0L || !repo.isLive) {
            out += MenuAction("View Sticker Set", TgIcons.CtxSmile) { StickerSetSheet.openSet(c.setId) }
        }
        is MessageContent.Photo -> if (c.loop) {
            val fileId = c.videoFile?.fileId ?: 0
            val saved = repo.savedGifs.any { it.fileId == fileId && fileId > 0 }
            if (saved) {
                out += MenuAction("Remove from GIFs", TgIcons.CtxSave) {
                    repo.removeSavedGif(fileId) { err -> if (err != null) toast.error(err) else toast.show("GIF removed") }
                }
            } else {
                out += MenuAction("Save to GIFs", TgIcons.CtxSave) {
                    repo.saveGif(fileId) { err -> if (err != null) toast.error(err) else toast.show("GIF saved to GIFs") }
                }
            }
        }
        else -> {}
    }
    return out
}

@Composable
private fun StickerSetSheetView(req: StickerSetSheet.Request) {
    val repo = LocalRepository.current
    val toast = LocalToast.current
    val view = LocalView.current
    val c = TgTheme.colors
    var set by remember { mutableStateOf<StickerSetInfo?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val dismiss = { StickerSetSheet.dismiss() }
    BackHandler(onBack = dismiss)

    LaunchedEffect(req) {
        var setId = req.setId
        if (req.customEmojiId != 0L) {
            // The emoji's pack id comes with the emoji itself (getCustomEmojiStickers).
            repo.loadCustomEmoji(listOf(req.customEmojiId))
            val emoji = withTimeoutOrNull(8_000) { snapshotFlow { repo.customEmoji(req.customEmojiId) }.filterNotNull().first() }
            setId = emoji?.setId ?: 0L
            if (setId == 0L) {
                failed = true
                return@LaunchedEffect
            }
        }
        repo.loadStickerSet(setId) { info ->
            if (info == null) failed = true else set = info
        }
    }

    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = dismiss)
        )
        AnimatedVisibility(
            shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.86f, 380f)) { it } + fadeIn(),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val maxSheet = maxHeight * 0.78f
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheet)
                        .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                        .background(c.groupedBackground)
                        // Swallow taps so they don't reach the dimmed background.
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .navigationBarsPadding(),
                ) {
                    // Grabber + header: close circle, centered set title
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 36.dp, height = 5.dp).clip(Capsule()).background(c.secondaryText.copy(alpha = 0.35f)))
                    }
                    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp)) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .size(36.dp)
                                .clip(Capsule())
                                .background(if (c.isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f))
                                .fadeClickable(onClick = dismiss),
                            contentAlignment = Alignment.Center,
                        ) { Icon(IosIcons.Close, c.text, 16.dp) }
                        val title = set?.title ?: if (req.customEmojiId != 0L) "Emoji" else "Stickers"
                        T(
                            title, TgTheme.type.headline.copy(textDirection = TextDirection.Content), c.text,
                            weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                        )
                    }
                    val s = set
                    when {
                        s != null -> {
                            SetGrid(s, Modifier.fillMaxWidth().weight(1f, fill = false))
                            val count = s.stickers.size
                            val noun = if (s.emoji) "Emoji" else if (count == 1) "Sticker" else "Stickers"
                            val label = if (s.installed) "Remove $count $noun" else "Add $count $noun"
                            Box(
                                Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(Capsule())
                                    .background(if (s.installed) (if (c.isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f)) else c.accent)
                                    .bounceClickable {
                                        if (busy) return@bounceClickable
                                        busy = true
                                        Haptics.tap(view)
                                        val install = !s.installed
                                        repo.setStickerSetInstalled(s.id, install) { err ->
                                            busy = false
                                            if (err != null) {
                                                toast.error(err)
                                            } else {
                                                set = s.copy(installed = install)
                                                toast.show(
                                                    if (install) "${s.title} added to your ${if (s.emoji) "emoji" else "stickers"}."
                                                    else "${s.title} removed from your ${if (s.emoji) "emoji" else "stickers"}."
                                                )
                                                StickerSetSheet.dismiss()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (busy) ActivityIndicator(20.dp, if (s.installed) c.secondaryText else Color.White)
                                else T(label, TgTheme.type.body, if (s.installed) c.destructive else Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                        failed -> Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, c.secondaryText, 34.dp)
                            Spacer(Modifier.height(10.dp))
                            T(
                                if (req.customEmojiId != 0L) "This emoji pack isn't available." else "This sticker set isn't available.",
                                TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center,
                            )
                        }
                        else -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            ActivityIndicator(24.dp)
                        }
                    }
                }
            }
        }
    }
}

/** The set's stickers (5 per row) or emoji (8 per row); a few Lottie animations run at once, the rest stay still. */
@Composable
private fun SetGrid(s: StickerSetInfo, modifier: Modifier) {
    val grid = rememberLazyGridState()
    val visible by rememberVisibleIndices(grid)
    val slots = remember { PlaybackSlots(if (s.emoji) 24 else 12) }
    LazyVerticalGrid(
        GridCells.Fixed(if (s.emoji) 8 else 5),
        state = grid,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = modifier,
    ) {
        items(s.stickers.size, key = { i -> "st${s.stickers[i].fileId}:$i" }) { i ->
            val st = s.stickers[i]
            val play = rememberPlaybackSlot(slots, i in visible)
            StickerArt(st, st.emoji, Modifier.aspectRatio(1f).padding(if (s.emoji) 4.dp else 6.dp), animate = play)
        }
    }
}
