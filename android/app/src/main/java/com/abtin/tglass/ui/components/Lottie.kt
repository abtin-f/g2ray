package com.abtin.tglass.ui.components

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.abtin.tglass.R
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.navigation.LocalScreenVisible
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

/**
 * Lottie animations taken from Telegram-iOS (`submodules/TelegramUI/Resources/Animations`,
 * `Telegram/Telegram-iOS/Resources`, GPL-2.0).
 */
object TgAnimations {
    val TabChats = R.raw.lottie_tab_chats
    val TabContacts = R.raw.lottie_tab_contacts
    val TabCalls = R.raw.lottie_tab_calls
    val TabSettings = R.raw.lottie_tab_settings
    val Archive = R.raw.lottie_anim_archive
    val Unarchive = R.raw.lottie_anim_unarchive
    val Pin = R.raw.lottie_anim_pin
    val Unpin = R.raw.lottie_anim_unpin
    val Mute = R.raw.lottie_anim_mute
    val Unmute = R.raw.lottie_anim_unmute
    val Read = R.raw.lottie_anim_read
    val Unread = R.raw.lottie_anim_unread
    val Delete = R.raw.lottie_anim_delete
    val SwipeReply = R.raw.lottie_anim_swipereply
    val ChatListEmpty = R.raw.lottie_chat_list_empty
    val PlaneLogo = R.raw.lottie_plane_logo
    val IntroMessage = R.raw.lottie_intro_message
    val IntroPhone = R.raw.lottie_intro_phone
    val IntroLetter = R.raw.lottie_intro_letter
}

/**
 * A single-color animated icon. Rests on the last frame; every time [playKey] changes (and [play] is true)
 * it plays once from the start — the way Telegram animates tab icons and swipe actions.
 */
@Composable
fun LottieIcon(
    @RawRes res: Int,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    playKey: Any? = null,
    play: Boolean = true,
    restAtEnd: Boolean = true,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val anim = rememberLottieAnimatable()
    val animations = LocalAppSettings.current.animations
    LaunchedEffect(composition, playKey, play) {
        val c = composition ?: return@LaunchedEffect
        if (play && animations && playKey != null) {
            anim.animate(c, initialProgress = 0f)
        } else {
            anim.snapTo(c, if (restAtEnd) 1f else 0f)
        }
    }
    // Remembered: a new filter instance on every recomposition would make Lottie re-apply its dynamic properties.
    val tintFilter = remember(tint) { PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_ATOP) }
    val props = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR_FILTER, tintFilter, "**"),
    )
    LottieAnimation(
        composition = composition,
        progress = { anim.progress },
        modifier = modifier.size(size),
        dynamicProperties = props,
        renderMode = RenderMode.HARDWARE,
    )
}

/** A full-color illustration that loops (welcome pages, empty states). */
@Composable
fun LottieLoop(@RawRes res: Int, size: Dp, modifier: Modifier = Modifier, iterations: Int = LottieConstants.IterateForever) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val animations = LocalAppSettings.current.animations
    // Paused while a screen above completely covers this one.
    val screenVisible by LocalScreenVisible.current
    val progress by animateLottieCompositionAsState(composition, iterations = iterations, isPlaying = animations && screenVisible)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier.size(size), renderMode = RenderMode.HARDWARE)
}

/** Decompressed Telegram animated stickers (.tgs = gzipped Lottie JSON), keyed by file path. */
private val tgsCache = android.util.LruCache<String, String>(48)

/**
 * A Telegram animated sticker / animated emoji from a local .tgs file, looping.
 * Shows [placeholder] until the animation is parsed (and when it can't be).
 */
@Composable
fun TgsSticker(path: String?, modifier: Modifier, placeholder: @Composable () -> Unit) {
    val json = androidx.compose.runtime.produceState(path?.let { tgsCache.get(it) }, path) {
        if (path == null || value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                java.util.zip.GZIPInputStream(java.io.File(path).inputStream()).bufferedReader().use { it.readText() }
            }.getOrNull()
        }?.also { tgsCache.put(path, it) }
    }.value
    if (json == null) {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) { placeholder() }
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(json), cacheKey = "tgs:$path")
    val animate = LocalAppSettings.current.animations
    if (composition == null) {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) { placeholder() }
        return
    }
    // At most a handful of stickers loop at once (the oldest on screen win); the rest rest on their first frame.
    val token = remember { Any() }
    // A covered screen's stickers neither loop nor hold a slot of the budget.
    val screenVisible by LocalScreenVisible.current
    DisposableEffect(token, screenVisible) {
        if (screenVisible) LottieBudget.active.add(token)
        onDispose { LottieBudget.active.remove(token) }
    }
    val allowed by remember(token) { derivedStateOf { LottieBudget.active.indexOf(token) in 0 until LottieBudget.MaxLoops } }
    val progress = remember { mutableFloatStateOf(0f) }
    // Own player at ~30 fps (the frame is only drawn, never recomposed): half the work of a 60 fps loop, same look.
    LaunchedEffect(composition, animate, allowed) {
        val c = composition ?: return@LaunchedEffect
        if (!animate || !allowed) return@LaunchedEffect
        val durationNs = (c.duration * 1_000_000f).coerceAtLeast(1f)
        var start = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now - (progress.floatValue * durationNs).toLong()
                if (now - last >= 32_000_000L) {
                    last = now
                    progress.floatValue = (((now - start) % durationNs.toLong()).toFloat() / durationNs).coerceIn(0f, 1f)
                }
            }
        }
    }
    LottieAnimation(composition, { progress.floatValue }, modifier, renderMode = RenderMode.HARDWARE)
}

/** Which animated stickers may loop right now (registration order = priority). */
private object LottieBudget {
    const val MaxLoops = 6
    val active = mutableStateListOf<Any>()
}
