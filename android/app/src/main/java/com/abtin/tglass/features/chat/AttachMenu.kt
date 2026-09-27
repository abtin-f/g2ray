package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.shapes.RoundedRectangle

/** What the attach menu asks [AttachSheet] to do. */
enum class AttachAction { Location, File, Contact, Camera, Gallery, Poll, Music, Gift }

private class AttachMenuItem(val action: AttachAction, val title: String, val icon: Int, val color: Color)

/**
 * The "+" menu of Telegram-iOS 26: a glass popup rising from the + button with a list of attachment types, each with a
 * colored round icon — an iOS 26 menu, not a bottom sheet. [bottom] is the distance of the + button's top from the
 * bottom of the screen.
 */
@Composable
fun AttachMenu(visible: Boolean, bottom: Dp, canPoll: Boolean, demo: Boolean, onDismiss: () -> Unit, onPick: (AttachAction) -> Unit) {
    val c = TgTheme.colors
    val view = LocalView.current
    BackHandler(enabled = visible) { onDismiss() }
    if (visible) {
        // Tap outside to close.
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onDismiss() })
    }
    val items = buildList {
        add(AttachMenuItem(AttachAction.Location, "Location", TgIcons.AttLocation, Color(0xFF34C759)))
        add(AttachMenuItem(AttachAction.File, "File", TgIcons.AttFile, Color(0xFF1E90FF)))
        add(AttachMenuItem(AttachAction.Contact, "Contact", TgIcons.AttContact, Color(0xFFFF6B3D)))
        add(AttachMenuItem(AttachAction.Camera, "Camera", TgIcons.AttCamera, Color(0xFF8E8E93)))
        add(AttachMenuItem(AttachAction.Gallery, "Gallery", TgIcons.AttGallery, Color(0xFFFF375F)))
        if (canPoll) add(AttachMenuItem(AttachAction.Poll, "Poll", TgIcons.AttPoll, Color(0xFFFFB800)))
        add(AttachMenuItem(AttachAction.Music, "Music", TgIcons.AttAudio, Color(0xFFFF3B30)))
        if (demo) add(AttachMenuItem(AttachAction.Gift, "Gift", TgIcons.AttGift, Color(0xFFAF52DE)))
    }
    Box(Modifier.fillMaxSize().padding(start = 10.dp, bottom = bottom), contentAlignment = Alignment.BottomStart) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.2f, transformOrigin = TransformOrigin(0f, 1f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.2f, transformOrigin = TransformOrigin(0f, 1f)),
        ) {
            GlassBox(
                onClick = null,
                shape = RoundedRectangle(30.dp),
                surface = if (c.isDark) Color(0xFF1C1C1E).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.width(236.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    items.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .iosClickable(highlight = c.text.copy(alpha = 0.06f)) {
                                    Haptics.tap(view)
                                    onPick(item.action)
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(item.color), contentAlignment = Alignment.Center) {
                                Icon(item.icon, Color.White, 19.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            T(item.title, TgTheme.type.body, c.text, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
