package com.abtin.tglass.features.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.abtin.tglass.core.navigation.LocalScreenVisible
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.ProfileDetails
import com.abtin.tglass.data.ProfileGift
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.VerifiedBadge
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.initials
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pull-down-to-expand state of the profile header (iOS: pulling the page down grows the round avatar into
 * a full-width photo). [pull] is the current overscroll in px; [expand] animates 0 = round avatar, 1 = photo.
 */
@Stable
internal class HeaderState(private val scope: CoroutineScope, private val isAtTop: () -> Boolean) {
    var pull by mutableFloatStateOf(0f)
        private set
    val expand = Animatable(0f)
    var expanded by mutableStateOf(false)
        private set
    /** Only people/chats with a photo expand. */
    var canExpand by mutableStateOf(false)
    /** Pull distance (px) that expands the photo on release. */
    var thresholdPx = 1f
    var maxPullPx = 1f
    /** True once the user scrolled/pulled; the default style then no longer changes by itself. */
    var touched = false
        private set
    private var releaseJob: Job? = null

    fun setExpanded(value: Boolean, animated: Boolean = true) {
        val v = value && canExpand
        if (v == expanded && expand.value == (if (v) 1f else 0f)) return
        expanded = v
        scope.launch {
            if (animated) expand.animateTo(if (v) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 300f))
            else expand.snapTo(if (v) 1f else 0f)
        }
    }

    /** Finger lifted: expand if pulled far enough, then spring the overscroll back. */
    fun release() {
        if (pull <= 0f || releaseJob?.isActive == true) return
        if (!expanded && canExpand && pull > thresholdPx) setExpanded(true)
        releaseJob = scope.launch {
            animate(pull, 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)) { v, _ -> pull = v }
        }
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && source == NestedScrollSource.UserInput) {
                touched = true
                if (pull > 0f) {
                    releaseJob?.cancel()
                    val d = maxOf(available.y, -pull)
                    pull += d
                    return Offset(0f, d)
                }
                if (expanded && isAtTop()) {
                    // Scrolling up on the big photo shrinks it back into the round avatar first.
                    setExpanded(false)
                    return Offset(0f, available.y)
                }
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && source == NestedScrollSource.UserInput) {
                touched = true
                releaseJob?.cancel()
                pull = (pull + available.y * 0.5f).coerceAtMost(maxPullPx)
                // iOS: the round avatar turns into the full-width photo as soon as the pull passes the threshold.
                if (!expanded && canExpand && pull > thresholdPx) setExpanded(true)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pull > 0f) {
                release()
                return available
            }
            return Velocity.Zero
        }
    }
}

/** Positions (dp) of the header parts for expansion [p] and overscroll [pull]. */
internal class HeaderGeometry(
    val avatarTop: Dp,
    val avatarWidth: Dp,
    val avatarHeight: Dp,
    val corner: Dp,
    val nameTop: Dp,
    val buttonsTop: Dp,
    val musicTop: Dp,
    val height: Dp,
)

/**
 * Profile buttons: round 48pt glass buttons (22pt glyph) with an 11pt label under each, spread evenly
 * across the width. [ButtonSize] is the height of the whole row (circle + gap + label).
 */
internal val ButtonCircle = 48.dp
internal val ButtonSize = 66.dp
internal val ButtonGap = 8.dp

internal fun headerGeometry(width: Dp, statusTop: Dp, p: Float, pull: Dp, hasMusic: Boolean): HeaderGeometry {
    val size0 = 100.dp + (pull * 0.35f).coerceAtMost(44.dp)
    val top0 = statusTop + 40.dp + pull * 0.6f
    val name0 = top0 + size0 + 12.dp
    val buttons0 = name0 + 62.dp
    val photoH = width * 1.1f + pull
    val buttons1 = photoH - ButtonSize - 26.dp
    val name1 = buttons1 - 66.dp
    val buttonsTop = lerp(buttons0, buttons1, p)
    val musicTop = buttonsTop + ButtonSize + 12.dp
    val height = (if (hasMusic) musicTop + 34.dp else buttonsTop + ButtonSize) + 14.dp
    return HeaderGeometry(
        avatarTop = lerp(top0, 0.dp, p),
        avatarWidth = lerp(size0, width, p),
        avatarHeight = lerp(size0, photoH, p),
        corner = lerp(size0 / 2, 0.dp, p),
        nameTop = lerp(name0, name1, p),
        buttonsTop = buttonsTop,
        musicTop = musicTop,
        height = height,
    )
}

/**
 * The top list item: avatar (round → full-width photo), floating gifts, name with badges, status and the
 * music line. The action buttons are drawn by [ProfileActionsOverlay] outside the backdrop layer.
 *
 * [geo], [p] and [palette] are providers: they are read only in layout / graphicsLayer / draw lambdas (and the
 * palette in the few text colors), so pulling the page down or expanding the photo re-lays-out and redraws this
 * item without recomposing it.
 */
@Composable
internal fun ProfileHeaderItem(
    geo: () -> HeaderGeometry,
    width: Dp,
    p: () -> Float,
    style: HeaderStyle,
    palette: () -> ProfilePalette,
    name: String,
    seed: Long,
    photoPeer: Long,
    saved: Boolean,
    subtitle: String?,
    online: Boolean,
    badges: @Composable () -> Unit,
    photo: ImageRef?,
    photoCount: Int,
    photoIndex: Int,
    gifts: List<ProfileGift>,
    musicLine: (@Composable () -> Unit)?,
    onAvatarTap: (xFraction: Float) -> Unit,
) {
    val repo = LocalRepository.current
    val (ga, gb) = if (saved) Color(0xFF72D5FD) to Color(0xFF2A9EF1) else avatarColors(seed)
    val small = if (saved) null else repo.avatar(photoPeer)
    val screenVisible by LocalScreenVisible.current
    val avatarBrush = remember(ga, gb) { Brush.verticalGradient(listOf(ga, gb)) }
    val showGifts by remember(style, gifts) { derivedStateOf { style == HeaderStyle.Colored && gifts.isNotEmpty() && p() < 1f } }
    val showScrim by remember { derivedStateOf { p() > 0f } }
    val pal = palette()
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val h = geo().height.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
    ) {
        // Floating gifts around the round avatar (colored profiles).
        if (showGifts) {
            val t = rememberInfiniteTransition(label = "gifts")
            // No running animation while another screen covers this one.
            val bob = if (screenVisible) t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "bob") else null
            gifts.take(GiftSpots.size).forEachIndexed { i, g ->
                val (dx, dy) = GiftSpots[i]
                Box(
                    Modifier
                        .offset {
                            val gm = geo()
                            val cx = width / 2
                            val cy = gm.avatarTop + gm.avatarHeight / 2
                            IntOffset((cx + dx.dp - 18.dp).roundToPx(), (cy + dy.dp - 18.dp).roundToPx())
                        }
                        .size(36.dp)
                        .graphicsLayer {
                            alpha = (1f - p() * 2f).coerceIn(0f, 1f)
                            translationY = (bob?.value ?: 0f) * (if (i % 2 == 0) 3f else -3f) * density
                        }
                        .drawBehind {
                            val glow = g.centerColor?.let { Color(it or 0xFF000000.toInt()) } ?: Color.White
                            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension * 0.75f)
                        },
                    contentAlignment = Alignment.Center,
                ) { GiftGlyph(g, 30.dp) }
            }
        }
        // Avatar / photo: position, size and corner radius follow the pull / expansion in layout + layer only.
        Box(
            Modifier
                .offset {
                    val gm = geo()
                    IntOffset(((width - gm.avatarWidth) / 2).roundToPx(), gm.avatarTop.roundToPx())
                }
                .layout { measurable, _ ->
                    val gm = geo()
                    val w = gm.avatarWidth.roundToPx()
                    val h = gm.avatarHeight.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(geo().corner)
                    clip = true
                }
                .background(avatarBrush)
                .pointerInput(Unit) { detectTapGestures { o -> onAvatarTap(o.x / size.width.coerceAtLeast(1)) } },
            contentAlignment = Alignment.Center,
        ) {
            // Glyph / initials are laid out for the 100dp round avatar and scaled with it (no per-frame text relayout).
            val glyphScale: GraphicsLayerScope.() -> Unit = {
                val gm = geo()
                val k = minOf(gm.avatarWidth.toPx(), gm.avatarHeight.toPx()) / 100.dp.toPx()
                scaleX = k
                scaleY = k
            }
            if (saved) Icon(TgIcons.SetSaved, Color.White, 55.dp, modifier = Modifier.graphicsLayer(glyphScale))
            else if (small == null && photo == null) {
                T(
                    initials(name), TgTheme.type.body.copy(fontSize = 40.sp, lineHeight = 46.sp), Color.White,
                    weight = FontWeight.SemiBold, modifier = Modifier.graphicsLayer(glyphScale),
                )
            }
            if (small != null) TgImage(small, Modifier.fillMaxSize(), maxPx = 320)
            if (photo != null && !saved) TgImage(photo, Modifier.fillMaxSize(), maxPx = 1280, contentScale = ContentScale.Crop)
            if (showScrim) {
                // Photo melts into the page and darkens under the name.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = p() }.drawBehind {
                        val page = palette().page
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.22f to Color.Transparent,
                                0.55f to Color.Transparent,
                                0.82f to page.copy(alpha = 0.55f),
                                1f to page,
                            )
                        )
                    }
                )
                if (photoCount > 1) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 6.dp, start = 10.dp, end = 10.dp).fillMaxWidth().graphicsLayer { alpha = p() },
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        repeat(photoCount.coerceAtMost(12)) { i ->
                            Box(
                                Modifier.weight(1f).height(2.5.dp).clip(Capsule())
                                    .background(Color.White.copy(alpha = if (i == photoIndex) 0.95f else 0.35f))
                            )
                        }
                    }
                }
            }
        }
        // Name, badges and status.
        Column(
            Modifier.fillMaxWidth().offset { IntOffset(0, geo().nameTop.roundToPx()) }.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.abtin.tglass.core.emoji.EmojiText(
                    name, TgTheme.type.title2.copy(fontSize = 26.sp, lineHeight = 31.sp), pal.title,
                    weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badges()
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                T(subtitle, TgTheme.type.subheadline, if (online) pal.online else pal.subtitle, maxLines = 1)
            }
        }
        if (musicLine != null) {
            Box(Modifier.fillMaxWidth().offset { IntOffset(0, geo().musicTop.roundToPx()) }, contentAlignment = Alignment.TopCenter) { musicLine() }
        }
    }
}

private val GiftSpots = listOf(-118f to -18f, 104f to -54f, 122f to 26f, -98f to 58f, 60f to 70f)

/** Emoji status / verified / premium marks after the name. */
@Composable
internal fun NameBadges(details: ProfileDetails?, verified: Boolean, premium: Boolean, palette: ProfilePalette, tinted: Boolean) {
    val status = details?.emojiStatus
    val glyph = details?.emojiStatusEmoji
    if (verified) {
        Spacer(Modifier.width(6.dp))
        VerifiedBadge(20.dp, color = if (tinted) Color.White.copy(alpha = 0.9f) else TgTheme.colors.accent)
    }
    when {
        status != null -> {
            Spacer(Modifier.width(6.dp))
            StickerGlyph(status, glyph ?: "⭐", 24.dp)
        }
        glyph != null -> {
            Spacer(Modifier.width(6.dp))
            T(glyph, TgTheme.type.body.copy(fontSize = 21.sp, lineHeight = 26.sp))
        }
        premium -> {
            Spacer(Modifier.width(6.dp))
            Icon(TgIcons.IcPremiumPeer, if (tinted) Color.White else palette.cardAccent, 20.dp)
        }
    }
}

/** A sticker (animated .tgs or static preview), or [fallback] emoji while it isn't available. */
@Composable
internal fun StickerGlyph(sticker: com.abtin.tglass.data.StickerItem, fallback: String, size: Dp) {
    val repo = LocalRepository.current
    val anim = sticker.animation
    val animPath = anim?.let { repo.filePath(it) }
    androidx.compose.runtime.LaunchedEffect(anim, animPath) { if (anim != null && animPath == null) repo.requestImage(anim) }
    val still: @Composable () -> Unit = {
        if (sticker.image != null) TgImage(sticker.image, Modifier.size(size), maxPx = 256, contentScale = ContentScale.Fit)
        else T(fallback, TgTheme.type.body.copy(fontSize = (size.value * 0.8f).sp, lineHeight = size.value.sp))
    }
    if (anim != null) com.abtin.tglass.ui.components.TgsSticker(animPath, Modifier.size(size), still)
    else Box(Modifier.size(size), contentAlignment = Alignment.Center) { still() }
}

/** A gift's sticker, or its emoji in the demo. */
@Composable
internal fun GiftGlyph(g: ProfileGift, size: Dp) {
    val s = g.sticker
    if (s != null) StickerGlyph(s, g.emoji, size)
    else Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        T(g.emoji, TgTheme.type.body.copy(fontSize = (size.value * 0.72f).sp, lineHeight = (size.value * 0.9f).sp))
    }
}

/** One glass tile under the name: [icon] with its lowercase [label] caption (Telegram iOS "call", "mute"…). */
internal class ProfileAction(val icon: Int, val label: String, val onClick: () -> Unit)

/**
 * Size to draw a profile-button drawable at so every glyph comes out ~24dp like Telegram iOS: the
 * `tg_pi_*` icons are a ~20-unit glyph in a 40-unit viewport, the video camera fills ~24 of its 30 units.
 */
private fun profileIconSize(icon: Int): Dp = if (icon == com.abtin.tglass.ui.components.TgIcons.PiVideo) 26.dp else 42.dp

/**
 * The row of glass buttons (call, video, mute, search, more). Drawn above the list — outside the
 * list's backdrop layer, so the glass may refract it — at [y] px from the top, fading by [alpha].
 */
@Composable
internal fun ProfileActionsOverlay(actions: List<ProfileAction>, paletteState: State<ProfilePalette>, y: () -> Int, alpha: () -> Float, enabled: Boolean) {
    val palette = paletteState.value
    Row(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, y()) }
            .graphicsLayer { this.alpha = alpha() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.CenterHorizontally),
    ) {
        actions.forEach { a ->
            Column(
                Modifier
                    .weight(1f)
                    .height(ButtonSize)
                    .then(if (enabled) Modifier.bounceClickable(a.onClick) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(ButtonCircle)
                        .profileGlass(com.kyant.shapes.Capsule(), palette.button, palette.buttonSolid),
                    contentAlignment = Alignment.Center,
                ) {
                    // The drawables carry their own padding: draw them unbounded so the glyph comes out ~22dp.
                    Icon(
                        a.icon, palette.buttonIcon, profileIconSize(a.icon),
                        modifier = Modifier.wrapContentSize(unbounded = true),
                        contentDescription = a.label,
                    )
                }
                Spacer(Modifier.height(4.dp))
                T(
                    a.label.lowercase(), TgTheme.type.caption2.copy(fontSize = 11.sp, lineHeight = 13.sp), palette.buttonIcon,
                    maxLines = 1, align = TextAlign.Center,
                )
            }
        }
    }
}
