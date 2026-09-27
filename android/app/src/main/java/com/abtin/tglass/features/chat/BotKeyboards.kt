package com.abtin.tglass.features.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.ui.components.TgIcons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.data.InlineButton
import com.abtin.tglass.data.InlineButtonKind
import com.abtin.tglass.data.InlineKeyboard
import com.abtin.tglass.data.ReplyKeyboard
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.max

/**
 * A message bubble with a bot's inline keyboard under it. The keyboard is as wide as the bubble
 * (at least [minKeyboardWidth], at most [maxWidth]) — measured directly, so no intrinsics are needed.
 */
@Composable
fun BubbleWithKeyboard(
    alignEnd: Boolean,
    maxWidth: Dp,
    minKeyboardWidth: Dp = 200.dp,
    bubble: @Composable () -> Unit,
    keyboard: @Composable () -> Unit,
) {
    Layout(content = { bubble(); keyboard() }) { ms, cons ->
        val b = ms[0].measure(cons.copy(minWidth = 0, minHeight = 0))
        val cap = if (cons.hasBoundedWidth) minOf(cons.maxWidth, maxWidth.roundToPx()) else maxWidth.roundToPx()
        val kw = max(b.width, minKeyboardWidth.roundToPx()).coerceAtMost(max(cap, b.width))
        val k = ms[1].measure(Constraints(minWidth = kw, maxWidth = kw))
        val width = max(b.width, k.width)
        layout(width, b.height + k.height) {
            b.place(if (alignEnd) width - b.width else 0, 0)
            k.place(if (alignEnd) width - k.width else 0, b.height)
        }
    }
}

/**
 * Telegram-iOS inline keyboard: rows of translucent rounded buttons under the bubble.
 * [busy] tells whether a button is waiting for the bot's answer (shows a spinner).
 */
@Composable
fun InlineKeyboardView(
    keyboard: InlineKeyboard,
    outgoing: Boolean,
    busy: (row: Int, col: Int) -> Boolean,
    onClick: (row: Int, col: Int, button: InlineButton) -> Unit,
) {
    // Drawn inside the message list, which is the backdrop layer the glass bars sample; glass here would sample
    // itself (endless render tree → native crash), so its buttons use the plain glass surface instead.
    androidx.compose.runtime.CompositionLocalProvider(com.abtin.tglass.core.glass.LocalBackdrop provides null) {
        InlineKeyboardContent(keyboard, outgoing, busy, onClick)
    }
}

@Composable
private fun InlineKeyboardContent(
    keyboard: InlineKeyboard,
    outgoing: Boolean,
    busy: (row: Int, col: Int) -> Boolean,
    onClick: (row: Int, col: Int, button: InlineButton) -> Unit,
) {
    val c = TgTheme.colors
    // Telegram's actionButtonsFillColor: the service tint (day) / black 42 % (night); no glass inside the list.
    val buttonFill = if (c.isDark) Color.Black.copy(alpha = 0.42f) else c.serviceBubble
    Column(
        Modifier
            .fillMaxWidth()
            // Line up with the bubble body, not its tail column.
            .padding(start = if (outgoing) 0.dp else TailWidth, end = if (outgoing) TailWidth else 0.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keyboard.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { col, b ->
                    val loading = busy(r, col)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedRectangle(if (keyboard.rows.lastIndex == r) 14.dp else 10.dp))
                            .background(buttonFill)
                            .bounceClickable { if (!loading) onClick(r, col, b) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loading) ActivityIndicator(16.dp, Color.White)
                        else T(
                            b.text,
                            TgTheme.type.subheadline.copy(fontSize = 15.sp, textDirection = TextDirection.Content),
                            Color.White, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center,
                        )
                        if (b.kind == InlineButtonKind.Url) {
                            // Small arrow in the corner, like Telegram's link buttons.
                            Icon(
                                IosIcons.ArrowUp, Color.White, 9.dp,
                                Modifier.align(Alignment.TopEnd).offset(x = 4.dp).padding(top = 5.dp).graphicsLayer { rotationZ = 45f },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A bot's custom reply keyboard, shown above the composer (tap sends the button's text). */
@Composable
fun ReplyKeyboardPanel(
    keyboard: ReplyKeyboard,
    onButton: (String, Boolean) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    GlassBox(onClick = null, shape = RoundedRectangle(24.dp), modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(keyboard.placeholder, TgTheme.type.footnote, c.secondaryText, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.size(28.dp).fadeClickable(onClick = onHide), contentAlignment = Alignment.Center) {
                    Icon(IosIcons.ChevronDown, c.secondaryText, 18.dp)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                keyboard.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { b ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(if (keyboard.resize) 42.dp else 48.dp)
                                    .clip(RoundedRectangle(12.dp))
                                    .background(c.text.copy(alpha = if (b.sendsText) 0.08f else 0.04f))
                                    .bounceClickable { onButton(b.text, b.sendsText) }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                T(
                                    b.text, TgTheme.type.subheadline,
                                    if (b.sendsText) c.text else c.secondaryText,
                                    maxLines = 2, align = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * iOS 26 bar in place of the composer ("Start", "Unblock", "Join Channel"): a centered glass capsule
 * with the action in accent semibold. Lives in the bottom overlay, outside the message list's backdrop layer.
 */
@Composable
fun ChatBottomBar(title: String, onClick: () -> Unit, color: Color = TgTheme.colors.accent) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        GlassBox(
            onClick = onClick,
            shape = Capsule(),
            modifier = Modifier.height(50.dp).widthIn(min = 220.dp),
        ) {
            T(title, TgTheme.type.body, color, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

/**
 * Channel bar for readers (Telegram iOS 26): glass circle for the discussion group on the left,
 * glass "Mute"/"Unmute" capsule in the middle, glass search circle on the right.
 */
@Composable
fun ChannelBottomBar(muted: Boolean, onDiscuss: (() -> Unit)?, onMute: () -> Unit, onSearch: () -> Unit) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(48.dp)) {
        if (onDiscuss != null) {
            GlassIconButton(TgIcons.PiMessage, onDiscuss, Modifier.align(Alignment.CenterStart), size = 48.dp, iconSize = 44.dp, contentDescription = "Discussion")
        }
        GlassBox(
            onClick = onMute,
            shape = Capsule(),
            modifier = Modifier.align(Alignment.Center).height(48.dp).widthIn(min = 112.dp),
        ) {
            T(if (muted) "Unmute" else "Mute", TgTheme.type.body, c.text, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(horizontal = 26.dp))
        }
        GlassIconButton(TgIcons.PiSearch, onSearch, Modifier.align(Alignment.CenterEnd), size = 48.dp, iconSize = 44.dp, contentDescription = "Search")
    }
}

/** Small glass button that brings back a hidden bot keyboard. */
@Composable
fun ShowBotKeyboardButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    GlassBox(onClick = onClick, shape = Capsule(), modifier = modifier.height(36.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(IosIcons.Keyboard, c.text, 18.dp)
            Spacer(Modifier.size(6.dp))
            T("Keyboard", TgTheme.type.footnote, c.text, weight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Like bounceClickable, plus a long-press action (send button → "Send Without Sound"). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bounceLongClickable(onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.6f, 600f), label = "bounce")
    val view = LocalView.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick?.let { lc -> { Haptics.longPress(view); lc() } },
            onClick = onClick,
        )
}
