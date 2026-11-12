package com.abtin.tglass.core.emoji

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import com.abtin.tglass.core.design.LocalAppSettings
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Caps how many Telegram reaction animations run at once (Lottie is heavy); extra ones fall back to the static emoji. */
object LottieBudget {
    private const val Max = 6
    private var used = 0
    fun acquire(): Boolean = if (used < Max) { used++; true } else false
    fun release() { if (used > 0) used-- }
}

private val oneShotCache = android.util.LruCache<String, String>(24)

/**
 * Plays a local .tgs file once, then calls [onDone]. Draws [fallback] while the file is missing or parsed, and instead of
 * playing when animations are off or too many players are already running (then [onDone] is called at once).
 */
@Composable
fun OneShotTgs(path: String?, modifier: Modifier, onDone: () -> Unit, fallback: @Composable () -> Unit) {
    val animations = LocalAppSettings.current.animations
    val granted = remember { animations && LottieBudget.acquire() }
    DisposableEffect(Unit) { onDispose { if (granted) LottieBudget.release() } }
    if (!granted) {
        LaunchedEffect(Unit) { onDone() }
        Box(modifier, contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val json by produceState<String?>(path?.let { oneShotCache.get(it) }, path) {
        if (path == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { java.util.zip.GZIPInputStream(java.io.File(path).inputStream()).bufferedReader().use { it.readText() } }.getOrNull()
        }?.also { oneShotCache.put(path, it) }
    }
    val text = json
    if (text == null) {
        Box(modifier, contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(text), cacheKey = "tgs:$path")
    val anim = rememberLottieAnimatable()
    LaunchedEffect(composition) {
        val c = composition ?: return@LaunchedEffect
        anim.animate(c, iterations = 1)
        onDone()
    }
    if (composition == null) Box(modifier, contentAlignment = Alignment.Center) { fallback() }
    else LottieAnimation(composition, { anim.progress }, modifier)
}

internal class ActiveEffect(val id: Long, val emoji: String, val path: String?, val center: Offset, val size: Dp)

/**
 * Big reaction effects (Telegram's "around" / "effect" animation) drawn above the message list.
 * [expect] is called when the user adds a reaction; the chip that then appears calls [consume] and [play].
 */
class ReactionEffectsState {
    private var pendingEmoji: String? = null
    private var pendingAt = 0L
    private var nextId = 0L
    internal val active = mutableStateListOf<ActiveEffect>()

    fun expect(emoji: String) {
        pendingEmoji = emoji.replace("️", "")
        pendingAt = System.currentTimeMillis()
    }

    /** True once for the reaction announced with [expect] (within a few seconds). */
    fun consume(emoji: String): Boolean {
        val e = pendingEmoji ?: return false
        if (System.currentTimeMillis() - pendingAt > 4000 || e != emoji.replace("️", "")) return false
        pendingEmoji = null
        return true
    }

    /** Starts an effect centered at [center] (window px); [path] null = a floating Apple emoji instead. */
    fun play(emoji: String, path: String?, center: Offset, size: Dp = 200.dp) {
        while (active.size >= 2) active.removeAt(0)
        active.add(ActiveEffect(nextId++, emoji, path, center, size))
    }
}

val LocalReactionEffects = staticCompositionLocalOf<ReactionEffectsState?> { null }

/** Draws the running effects; place it above the list layer (not inside the recorded backdrop layer). */
@Composable
fun ReactionEffectsOverlay(state: ReactionEffectsState) {
    if (state.active.isEmpty()) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        for (e in state.active.toList()) {
            key(e.id) { EffectView(e, origin) { state.active.remove(e) } }
        }
    }
}

@Composable
private fun EffectView(e: ActiveEffect, origin: Offset, onDone: () -> Unit) {
    val density = LocalDensity.current
    val side = with(density) { e.size.toPx() }
    val x = (e.center.x - origin.x - side / 2f).roundToInt()
    val y = (e.center.y - origin.y - side / 2f).roundToInt()
    val mod = Modifier.offset { IntOffset(x, y) }.size(e.size)
    if (e.path != null) {
        OneShotTgs(e.path, mod, onDone) {}
    } else {
        // No TGS (demo / not downloaded): the Apple emoji swells and floats up.
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) { p.animateTo(1f, tween(900, easing = LinearEasing)); onDone() }
        Box(mod.graphicsLayer {
            val s = 0.6f + 1.1f * p.value
            scaleX = s; scaleY = s
            translationY = -side * 0.35f * p.value
            alpha = (1f - p.value).coerceIn(0f, 1f)
        }, contentAlignment = Alignment.Center) { EmojiGlyph(e.emoji, e.size / 3) }
    }
}
