package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

private val GalleryEmojis = listOf("🏔", "🌅", "🏝", "🌃", "🐈", "🍜", "🌸", "🚗", "🎨", "🏙", "🌊", "🎂", "🐕", "🌵", "🎸", "🌌", "🏖", "🍩", "⛰", "🗼")

/** Attachment panel: gallery grid with multi-select and the attachment type bar. */
@Composable
fun AttachSheet(visible: Boolean, onDismiss: () -> Unit, onSend: (List<MessageContent>) -> Unit) {
    val c = TgTheme.colors
    val selected = remember { mutableStateListOf<Int>() }
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)).clickable(remember { MutableInteractionSource() }, null) { onDismiss() })
    }
    AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            GlassBox(
                onClick = null,
                shape = RoundedRectangle(34.dp),
                surface = if (c.isDark) Color(0xFF1C1C1E).copy(0.9f) else Color.White.copy(0.9f),
                modifier = Modifier.padding(6.dp).navigationBarsPadding().fillMaxWidth().height(520.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(IosIcons.Close, onDismiss, size = 40.dp, iconSize = 18.dp)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            T(if (selected.isEmpty()) "Recents" else "${selected.size} Selected", TgTheme.type.headline, c.text)
                        }
                        Spacer(Modifier.width(40.dp))
                    }
                    LazyVerticalGrid(
                        GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedRectangle(6.dp)).background(Color(0xFF111111)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(TgIcons.AttCamera, Color.White, 34.dp) }
                        }
                        items(GalleryEmojis.indices.toList()) { i ->
                            val (a, b) = avatarColors(i.toLong())
                            val index = selected.indexOf(i)
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedRectangle(6.dp))
                                    .background(Brush.linearGradient(listOf(a, b)))
                                    .clickable(remember { MutableInteractionSource() }, null) {
                                        if (index >= 0) selected.remove(i) else selected.add(i)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                T(GalleryEmojis[i], TgTheme.type.body.copy(fontSize = 40.sp, lineHeight = 46.sp))
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .then(if (index >= 0) Modifier.background(c.accent) else Modifier.background(Color.Black.copy(0.15f)))
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (index >= 0) T("${index + 1}", TgTheme.type.footnote, Color.White, weight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (selected.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(44.dp).clip(Capsule()).background(c.searchField).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                T("Add a caption…", TgTheme.type.body, c.secondaryText)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.height(44.dp).clip(Capsule()).background(c.accent).bounceClickable {
                                    val items = selected.map { i -> MessageContent.Photo(i, listOf(1.33f, 0.75f, 1f, 1.5f)[i % 4], null, GalleryEmojis[i]) }
                                    selected.clear()
                                    onSend(items)
                                }.padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) { T("Send ${selected.size}", TgTheme.type.headline, Color.White) }
                        }
                    } else {
                        // iOS 26: attachment types live in a glass capsule, the current one tinted with the accent.
                        GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth().height(64.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                AttachType(TgIcons.AttGallery, "Gallery", selected = true) {}
                                AttachType(TgIcons.AttFile, "File") { onSend(listOf(MessageContent.File("Document.pdf", "2.4 MB"))) }
                                AttachType(TgIcons.AttLocation, "Location") { onSend(listOf(MessageContent.Location("Current Location", "Azadi Tower, Tehran"))) }
                                AttachType(TgIcons.AttPoll, "Poll") { onSend(listOf(MessageContent.Poll("What should we build next?", listOf("Stories editor", "Video calls", "Themes"), listOf(3, 5, 2)))) }
                                AttachType(TgIcons.AttContact, "Contact") { onSend(listOf(MessageContent.Contact("Sara Ahmadi", "+98 912 111 2233"))) }
                                AttachType(TgIcons.AttGift, "Gift") { onSend(listOf(MessageContent.Sticker("🎁"))) }
                                AttachType(TgIcons.AttAudio, "Music") { onSend(listOf(MessageContent.File("Song.mp3", "4.8 MB"))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachType(icon: Int, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = TgTheme.colors
    val tint = if (selected) c.accent else c.text
    Column(Modifier.width(46.dp).fadeClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, tint, 26.dp)
        Spacer(Modifier.height(2.dp))
        T(label, TgTheme.type.caption2.copy(fontSize = 10.sp), tint, maxLines = 1, weight = FontWeight.SemiBold)
    }
}
