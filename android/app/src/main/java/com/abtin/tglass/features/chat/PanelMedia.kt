package com.abtin.tglass.features.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.emoji.EmojiGlyph
import com.abtin.tglass.core.media.VideoSurface
import com.abtin.tglass.core.media.rememberVideoPlayer
import com.abtin.tglass.core.media.rememberVideoState
import com.abtin.tglass.data.GifItem
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.StickerItem
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.TgsSticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * At most [max] animations (GIF players / Lottie stickers) run at once in a panel grid; the other cells show their
 * still picture. A cell holds a slot only while it is on screen.
 */
@Stable
internal class PlaybackSlots(val max: Int) {
    private val holders = mutableStateListOf<Any>()
    val used: Int get() = holders.size
    fun holds(token: Any): Boolean = holders.contains(token)
    fun tryAcquire(token: Any): Boolean {
        if (holders.contains(token)) return true
        if (holders.size >= max) return false
        holders.add(token)
        return true
    }
    fun release(token: Any) {
        holders.remove(token)
    }
}

/**
 * True while this cell may animate: it is [visible], stayed so for a moment (no players churn during a fling) and
 * got one of the [slots].
 */
@Composable
internal fun rememberPlaybackSlot(slots: PlaybackSlots, visible: Boolean): Boolean {
    val token = remember { Any() }
    DisposableEffect(slots, visible) {
        onDispose { slots.release(token) }
    }
    LaunchedEffect(slots, visible) {
        if (!visible) return@LaunchedEffect
        delay(160)
        while (!slots.tryAcquire(token)) {
            snapshotFlow { slots.used }.first { it < slots.max }
        }
    }
    return visible && slots.holds(token)
}

/** Indices of the grid items currently on screen. */
@Composable
internal fun rememberVisibleIndices(grid: LazyGridState): State<Set<Int>> = remember(grid) {
    derivedStateOf<Set<Int>>(structuralEqualityPolicy()) { grid.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.index } }
}

/** A saved / found GIF: its still first, then the MP4 looping muted (cropped to the cell) once it is downloaded. */
@Composable
internal fun GifCell(g: GifItem, play: Boolean, modifier: Modifier) {
    val repo = LocalRepository.current
    val autoplay = LocalAppSettings.current.autoplayGif
    val file = remember(g.fileId) { ImageRef(g.fileId) }
    val animate = play && autoplay && g.fileId > 0
    val path = if (animate) repo.filePath(file) else null
    LaunchedEffect(g.fileId, animate, path) { if (animate && path == null) repo.requestImage(file) }
    Box(modifier) {
        TgImage(g.thumb, Modifier.fillMaxSize(), maxPx = 256)
        if (animate && path != null) {
            val aspect = if (g.width > 0 && g.height > 0) g.width.toFloat() / g.height else 1f
            LoopingVideo(path, aspect, Modifier.fillMaxSize())
        }
    }
}

/** Muted looping video filling (cropping to) its box, faded in on its first frame. */
@Composable
private fun LoopingVideo(path: String, aspect: Float, modifier: Modifier) {
    val player = rememberVideoPlayer(path, loop = true, muted = true)
    val state = rememberVideoState(player)
    val alpha by animateFloatAsState(if (state.firstFrame) 1f else 0f, tween(180), label = "gifFrame")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val a = if (state.videoAspect > 0f) state.videoAspect else aspect
        val w = if (a >= 1f) maxHeight * a else maxWidth
        val h = if (a >= 1f) maxHeight else maxWidth / a
        VideoSurface(player, Modifier.requiredSize(w, h).graphicsLayer { this.alpha = alpha })
    }
}

/**
 * A sticker of the panel: animated (TGS/Lottie, looping) while [play] — reusing the bubbles' [TgsSticker] —
 * otherwise its still picture. Video (WEBM) stickers stay on their still thumbnail: Android's decoders drop the
 * VP9 alpha channel, so they would play on a black square.
 */
@Composable
internal fun StickerCell(st: StickerItem, play: Boolean, modifier: Modifier) {
    val repo = LocalRepository.current
    val animations = LocalAppSettings.current.animations
    val anim = st.animation
    val animate = play && animations && anim != null
    val path = if (animate && anim != null) repo.filePath(anim) else null
    LaunchedEffect(anim, animate, path) { if (animate && anim != null && path == null) repo.requestImage(anim) }
    val still: @Composable () -> Unit = {
        if (st.image != null) TgImage(st.image, Modifier.fillMaxSize(), maxPx = 192, contentScale = ContentScale.Fit)
        else EmojiGlyph(st.emoji, 40.dp)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (animate && path != null) TgsSticker(path, Modifier.fillMaxSize(), still) else still()
    }
}
