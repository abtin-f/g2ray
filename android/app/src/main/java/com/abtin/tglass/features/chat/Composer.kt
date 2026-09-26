package com.abtin.tglass.features.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.data.Message
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.SegmentedControl
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    replyTo: Message?,
    replyName: String?,
    editing: Message?,
    onCancelContext: () -> Unit,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onVoice: (Int) -> Unit,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }

    LaunchedEffect(recording) {
        while (recording) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(100)
        }
    }

    fun stopRecording(send: Boolean) {
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        recording = false
        locked = false
        scope.launch { dragX.animateTo(0f); dragY.animateTo(0f) }
        if (send && secs >= 1) {
            Haptics.confirm(view)
            onVoice(secs)
        } else if (send) {
            Haptics.reject(view)
        }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        // Reply / edit context (spec §20)
        AnimatedVisibility(visible = (replyTo != null || editing != null) && !recording, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val target = editing ?: replyTo
            GlassBox(onClick = null, shape = RoundedRectangle(20.dp), modifier = Modifier.padding(start = 54.dp, end = 54.dp, bottom = 6.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (editing != null) TgIcons.CtxEdit else TgIcons.CtxReply, c.accent, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(2.dp).height(32.dp).clip(Capsule()).background(c.accent))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        T(if (editing != null) "Edit Message" else "Reply to ${replyName ?: ""}", TgTheme.type.footnote.copy(fontSize = 14.sp), c.accent, weight = FontWeight.SemiBold, maxLines = 1)
                        T(target?.preview ?: "", TgTheme.type.footnote.copy(fontSize = 14.sp), c.text, maxLines = 1)
                    }
                    Box(Modifier.size(32.dp).fadeClickable(onClick = onCancelContext), contentAlignment = Alignment.Center) {
                        Icon(IosIcons.Close, c.secondaryText, 18.dp)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            // Attach
            AnimatedVisibility(!recording, enter = fadeIn(), exit = fadeOut()) {
                GlassIconButton(IosIcons.Paperclip, onAttach, size = 44.dp, iconSize = 24.dp, tint = c.text)
            }
            Spacer(Modifier.width(if (recording) 0.dp else 8.dp))

            // Field or recording status
            GlassBox(onClick = null, shape = RoundedRectangle(22.dp), modifier = Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                if (recording) {
                    RecordingStatus(elapsed, locked, dragX.value, onCancel = { stopRecording(false) })
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
                            if (text.isEmpty()) T("Message", TgTheme.type.body, c.secondaryText, maxLines = 1)
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                textStyle = TgTheme.type.body.copy(color = c.text),
                                cursorBrush = SolidColor(c.accent),
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) },
                            )
                        }
                        Box(Modifier.size(34.dp).padding(bottom = 5.dp).fadeClickable(onClick = onTogglePanel), contentAlignment = Alignment.Center) {
                            if (panelOpen) Icon(IosIcons.Keyboard, c.secondaryText, 26.dp) else Icon(TgIcons.InStickers, c.secondaryText, 26.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            // Send / mic
            val showSend = text.isNotBlank() || editing != null || locked
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (recording && !locked) {
                    // Lock hint above the mic
                    val lockAlpha = (1f + dragY.value / with(density) { 100.dp.toPx() }).coerceIn(0.4f, 1f)
                    GlassBox(
                        onClick = null,
                        shape = Capsule(),
                        modifier = Modifier
                            .requiredSize(38.dp, 76.dp)
                            .offset { IntOffset(0, (-110).dp.roundToPx() + (dragY.value * 0.5f).roundToInt()) }
                            .graphicsLayer { alpha = lockAlpha },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(IosIcons.Lock, c.secondaryText, 18.dp)
                            Icon(IosIcons.ChevronDown, c.secondaryText, 16.dp, Modifier.graphicsLayer { rotationZ = 180f })
                        }
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = 2.1f; scaleY = 2.1f
                                translationX = dragX.value
                                translationY = dragY.value
                            }
                            .clip(CircleShape)
                            .background(c.accent)
                    )
                }
                AnimatedContent(
                    targetState = showSend,
                    transitionSpec = { (scaleIn(initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut()) },
                    label = "sendMic",
                ) { send ->
                    if (send) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(c.accent).bounceClickable {
                                if (locked) stopRecording(true) else onSend()
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (editing != null) IosIcons.Checkmark else IosIcons.ArrowUp, Color.White, 22.dp)
                        }
                    } else {
                        val micModifier = Modifier.pointerInput(Unit) {
                            val cancelAt = 110.dp.toPx()
                            val lockAt = 90.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                startedAt = System.currentTimeMillis()
                                elapsed = 0
                                recording = true
                                Haptics.longPress(view)
                                var outcome = 0 // 0 = send, 1 = cancel, 2 = lock
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    ch.consume()
                                    val dx = (ch.position.x - down.position.x).coerceAtMost(0f)
                                    val dy = (ch.position.y - down.position.y).coerceAtMost(0f)
                                    scope.launch { dragX.snapTo(dx); dragY.snapTo(dy) }
                                    if (-dx > cancelAt) { outcome = 1; break }
                                    if (-dy > lockAt) { outcome = 2; break }
                                }
                                when (outcome) {
                                    1 -> stopRecording(false)
                                    2 -> {
                                        locked = true
                                        Haptics.tap(view)
                                        scope.launch { dragX.animateTo(0f); dragY.animateTo(0f) }
                                    }
                                    else -> stopRecording(true)
                                }
                            }
                        }
                        Box(Modifier.size(44.dp).then(micModifier), contentAlignment = Alignment.Center) {
                            if (recording) {
                                Box(Modifier.graphicsLayer { translationX = dragX.value; translationY = dragY.value }) {
                                    Icon(IosIcons.Mic, Color.White, 28.dp)
                                }
                            } else {
                                GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.size(44.dp)) {
                                    Icon(IosIcons.Mic, c.text, 24.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingStatus(elapsedMs: Long, locked: Boolean, dragX: Float, onCancel: () -> Unit) {
    val c = TgTheme.colors
    val blink = rememberInfiniteTransition(label = "rec")
    val alpha by blink.animateFloat(1f, 0.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot")
    val secs = elapsedMs / 1000
    val tenth = (elapsedMs % 1000) / 100
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(c.destructive))
        Spacer(Modifier.width(8.dp))
        T("%d:%02d,%d".format(secs / 60, secs % 60, tenth), TgTheme.type.body, c.text, maxLines = 1)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (locked) {
                T("Cancel", TgTheme.type.body, c.accent, modifier = Modifier.fadeClickable(onClick = onCancel))
            } else {
                Row(
                    Modifier.graphicsLayer { translationX = dragX * 0.6f; this.alpha = (1f + dragX / 400f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(IosIcons.ChevronLeft, c.secondaryText, 16.dp)
                    T("Slide to cancel", TgTheme.type.subheadline, c.secondaryText, maxLines = 1)
                }
            }
        }
    }
}

private val EmojiSet = ("😀 😃 😄 😁 😆 🥹 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 🙂‍↕️ 😏 😒 🙂‍↔️ 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😮‍💨 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🫣 🤗 🫡 🤔 🫢 🤭 🤫 🤥 😶 😐 😑 😬 🫨 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 👽 👾 🤖 🎃 😺 😸 😹 😻 😼 😽 🙀 😿 😾 " +
    "👋 🤚 🖐 ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 💪 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 💯 💥 🔥 ✨ 🎉 🎁 🏆 ⚽️ 🍕 🍔 ☕️ 🍰 🌸 🌹 🌈 ☀️ 🌙 ⭐️ ⚡️ ❄️ 🚀 ✈️ 🏔 🏝 📱 💻 🎧 🎮 📷").split(' ').filter { it.isNotBlank() }

private val StickerSet = listOf("🥳", "😎", "🤩", "😂", "😍", "🙏", "👍", "🔥", "💯", "🎉", "😭", "🤯", "🥰", "😴", "🤔", "👻", "🐱", "🐶", "🦊", "🐼", "🐸", "🦄", "🍕", "☕️")

/** Emoji / Stickers / GIF panel shown in place of the keyboard (spec §25). */
@Composable
fun EmojiPanel(onEmoji: (String) -> Unit, onSticker: (String) -> Unit, onGif: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    var tab by remember { mutableIntStateOf(0) }
    GlassBox(onClick = null, shape = RoundedRectangle(28.dp), modifier = modifier.fillMaxWidth().height(310.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth()) {
            SegmentedControl(listOf("Emoji", "Stickers", "GIFs"), tab, { tab = it }, Modifier.padding(horizontal = 40.dp, vertical = 10.dp).fillMaxWidth())
            when (tab) {
                0 -> LazyVerticalGrid(
                    GridCells.Fixed(8),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                ) {
                    items(EmojiSet) { e ->
                        Box(Modifier.aspectRatio(1f).clip(RoundedRectangle(10.dp)).bounceClickable { onEmoji(e) }, contentAlignment = Alignment.Center) {
                            T(e, TgTheme.type.body.copy(fontSize = 28.sp, lineHeight = 32.sp))
                        }
                    }
                }
                1 -> LazyVerticalGrid(
                    GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                ) {
                    items(StickerSet) { e ->
                        Box(Modifier.aspectRatio(1f).bounceClickable { onSticker(e) }, contentAlignment = Alignment.Center) {
                            T(e, TgTheme.type.body.copy(fontSize = 54.sp, lineHeight = 60.sp))
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                ) {
                    items((0 until 12).toList()) { i ->
                        val (a, b) = avatarColors(i.toLong() + 3)
                        Box(
                            Modifier.aspectRatio(1.4f).clip(RoundedRectangle(12.dp)).background(Brush.linearGradient(listOf(a, b))).bounceClickable { onGif(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            T(StickerSet[i % StickerSet.size], TgTheme.type.body.copy(fontSize = 40.sp, lineHeight = 46.sp))
                            T("GIF", TgTheme.type.caption2, Color.White, weight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Capsule()).background(Color.Black.copy(0.35f)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            }
        }
    }
}
