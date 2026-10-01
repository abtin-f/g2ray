package com.abtin.tglass.core.emoji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.data.CustomReactions
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.StickerItem
import com.abtin.tglass.features.chat.StickerSetSheet
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.TgsSticker

/**
 * A custom (premium) emoji drawn [size]×[size]: fetched with getCustomEmojiStickers (batched by the repository),
 * animated when it is a Lottie (TGS) emoji, its picture for WEBP ones (video emoji show their still thumbnail),
 * and the Apple emoji of [fallback] (or of the emoji's own alt text) while it loads.
 * Plain drawing only (no glass), so it can sit inside message bubbles.
 */
@Composable
fun CustomEmoji(id: Long, size: Dp, modifier: Modifier = Modifier, fallback: String? = null) {
    CustomEmoji(id, modifier.size(size), fallback)
}

/** [CustomEmoji] filling the size given by [modifier]. */
@Composable
fun CustomEmoji(id: Long, modifier: Modifier, fallback: String?) {
    val repo = LocalRepository.current
    val item = repo.customEmoji(id)
    LaunchedEffect(id, item == null) { if (item == null) repo.loadCustomEmoji(listOf(id)) }
    StickerArt(item, fallback ?: item?.emoji.orEmpty(), modifier)
}

/**
 * A sticker / custom emoji picture: the looping Lottie animation when there is one (and animations are on),
 * otherwise the still picture, and the Apple emoji [fallback] until something is downloaded.
 */
@Composable
fun StickerArt(st: StickerItem?, fallback: String, modifier: Modifier, animate: Boolean = true) {
    val repo = LocalRepository.current
    val animations = LocalAppSettings.current.animations
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight).let { if (it == Dp.Infinity) 20.dp else it }
        val image = st?.image
        val imagePath = image?.let { repo.filePath(it) }
        LaunchedEffect(image?.fileId, imagePath) { if (image != null && imagePath == null) repo.requestImage(image) }
        val still: @Composable () -> Unit = {
            if (image != null && imagePath != null) TgImage(image, Modifier.fillMaxSize(), maxPx = 256, contentScale = ContentScale.Fit)
            else if (fallback.isNotEmpty()) EmojiGlyph(fallback, side)
        }
        val anim = st?.animation
        val play = animate && animations && anim != null
        val animPath = if (play && anim != null) repo.filePath(anim) else null
        LaunchedEffect(anim?.fileId, play, animPath) { if (play && anim != null && animPath == null) repo.requestImage(anim) }
        if (animPath != null) TgsSticker(animPath, Modifier.fillMaxSize(), still) else still()
    }
}

/** Inline custom emoji of a text (see [appendWithAppleEmoji]); [tappable] ones open their emoji pack. */
@Composable
internal fun CustomEmojiInline(id: Long, alt: String, tappable: Boolean) {
    val m = if (tappable) {
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { StickerSetSheet.openEmoji(id) }
    } else {
        Modifier.fillMaxSize()
    }
    CustomEmoji(id, m, alt)
}

/** A reaction's emoji: a custom emoji for [CustomReactions] keys, otherwise the (Apple) emoji itself. */
@Composable
fun ReactionGlyph(reaction: String, size: Dp, modifier: Modifier = Modifier) {
    val id = CustomReactions.idOf(reaction)
    if (id != null) CustomEmoji(id, size, modifier) else EmojiGlyph(reaction, size, modifier)
}

/** The premium emoji of [entities] as inline spans (for [EmojiText] / [appendWithAppleEmoji]). */
fun customEmojiSpans(entities: List<Entity>): List<CustomEmojiSpan> =
    entities.mapNotNull { e -> if (e.type == EntityType.CustomEmoji && e.customEmojiId != 0L) CustomEmojiSpan(e.start, e.end, e.customEmojiId) else null }

/** Premium emoji of a message's text (matching [Message.preview] offsets for text and link messages). */
fun customEmojiSpans(m: Message): List<CustomEmojiSpan> = when (val c = m.content) {
    is MessageContent.Text -> customEmojiSpans(c.entities)
    is MessageContent.Link -> customEmojiSpans(c.entities)
    else -> emptyList()
}

/** [customEmojiSpans] of [m] shifted for `text.trim()` (big emoji-only messages). */
fun customEmojiSpansTrimmed(m: Message, text: String): List<CustomEmojiSpan> {
    val spans = customEmojiSpans(m)
    if (spans.isEmpty()) return spans
    val lead = text.length - text.trimStart().length
    val len = text.trim().length
    return spans.mapNotNull { s ->
        val a = s.start - lead
        val b = s.end - lead
        if (a >= 0 && b <= len) CustomEmojiSpan(a, b, s.id) else null
    }
}
