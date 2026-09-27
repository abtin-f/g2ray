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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.graphicsLayer
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

internal val ButtonSize = 54.dp

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
 */
@Composable
internal fun ProfileHeaderItem(
    geo: HeaderGeometry,
    width: Dp,
    p: Float,
    style: HeaderStyle,
    palette: ProfilePalette,
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
    Box(Modifier.fillMaxWidth().height(geo.height)) {
        // Floating gifts around the round avatar (colored profiles).
        if (style == HeaderStyle.Colored && gifts.isNotEmpty() && p < 1f) {
            val cx = width / 2
            val cy = geo.avatarTop + geo.avatarHeight / 2
            val spots = listOf(-118f to -18f, 104f to -54f, 122f to 26f, -98f to 58f, 60f to 70f)
            val t = rememberInfiniteTransition(label = "gifts")
            val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "bob")
            gifts.take(spots.size).forEachIndexed { i, g ->
                val (dx, dy) = spots[i]
                Box(
                    Modifier
                        .offset(x = cx + dx.dp - 18.dp, y = cy + dy.dp - 18.dp)
                        .size(36.dp)
                        .graphicsLayer {
                            alpha = (1f - p * 2f).coerceIn(0f, 1f)
                            translationY = bob * (if (i % 2 == 0) 3f else -3f) * density
                        }
                        .drawBehind {
                            val glow = g.centerColor?.let { Color(it or 0xFF000000.toInt()) } ?: Color.White
                            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension * 0.75f)
                        },
                    contentAlignment = Alignment.Center,
                ) { GiftGlyph(g, 30.dp) }
            }
        }
        // Avatar / photo.
        Box(
            Modifier
                .offset(x = (width - geo.avatarWidth) / 2, y = geo.avatarTop)
                .size(geo.avatarWidth, geo.avatarHeight)
                .clip(RoundedCornerShape(geo.corner))
                .background(Brush.verticalGradient(listOf(ga, gb)))
                .pointerInput(Unit) { detectTapGestures { o -> onAvatarTap(o.x / size.width.coerceAtLeast(1)) } },
            contentAlignment = Alignment.Center,
        ) {
            if (saved) Icon(TgIcons.SetSaved, Color.White, geo.avatarWidth.coerceAtMost(geo.avatarHeight) * 0.55f)
            else if (small == null && photo == null) {
                val s = geo.avatarWidth.coerceAtMost(geo.avatarHeight)
                T(initials(name), TgTheme.type.body.copy(fontSize = (s.value * 0.4f).sp, lineHeight = (s.value * 0.46f).sp), Color.White, weight = FontWeight.SemiBold)
            }
            if (small != null) TgImage(small, Modifier.fillMaxSize(), maxPx = 320)
            if (photo != null && !saved) TgImage(photo, Modifier.fillMaxSize(), maxPx = 1280, contentScale = ContentScale.Crop)
            if (p > 0f) {
                // Photo melts into the page and darkens under the name.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = p }.background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.22f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.82f to palette.page.copy(alpha = 0.55f),
                            1f to palette.page,
                        )
                    )
                )
                if (photoCount > 1) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 6.dp, start = 10.dp, end = 10.dp).fillMaxWidth().graphicsLayer { alpha = p },
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
            Modifier.fillMaxWidth().offset(y = geo.nameTop).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(
                    name, TgTheme.type.title2.copy(fontSize = 26.sp, lineHeight = 31.sp), palette.title,
                    weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badges()
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                T(subtitle, TgTheme.type.subheadline, if (online) palette.online else palette.subtitle, maxLines = 1)
            }
        }
        if (musicLine != null) {
            Box(Modifier.fillMaxWidth().offset(y = geo.musicTop), contentAlignment = Alignment.TopCenter) { musicLine() }
        }
    }
}

/** Emoji status / verified / premium marks after the name. */
@Composable
internal fun NameBadges(details: ProfileDetails?, verified: Boolean, premium: Boolean, palette: ProfilePalette, tinted: Boolean) {
    val status = details?.emojiStatus
    val glyph = details?.emojiStatusEmoji
    if (verified) {
        Spacer(Modifier.width(6.dp))
        VerifiedBadge(22.dp, color = if (tinted) Color.White.copy(alpha = 0.9f) else TgTheme.colors.accent)
    }
    when {
        status != null -> {
            Spacer(Modifier.width(6.dp))
            StickerGlyph(status, glyph ?: "⭐", 26.dp)
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

/** One round glass button under the name. */
internal class ProfileAction(val icon: Int, val label: String, val onClick: () -> Unit)

/**
 * The row of glass circle buttons (call, video, mute, search, more). Drawn above the list — outside the
 * list's backdrop layer, so the glass may refract it — at [y] px from the top, fading by [alpha].
 */
@Composable
internal fun ProfileActionsOverlay(actions: List<ProfileAction>, palette: ProfilePalette, y: () -> Int, alpha: () -> Float, enabled: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, y()) }
            .graphicsLayer { this.alpha = alpha() },
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        actions.forEach { a ->
            Box(
                Modifier
                    .size(ButtonSize)
                    .then(if (enabled) Modifier.bounceClickable(a.onClick) else Modifier)
                    .profileGlass(Capsule(), palette.button, palette.buttonSolid),
                contentAlignment = Alignment.Center,
            ) {
                Icon(a.icon, palette.buttonIcon, 28.dp, contentDescription = a.label)
            }
        }
    }
}
