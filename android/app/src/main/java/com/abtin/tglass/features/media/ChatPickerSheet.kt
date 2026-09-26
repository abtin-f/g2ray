package com.abtin.tglass.features.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.features.chatlist.ChatAvatar
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.SearchField
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.iosClickable

/** iOS share-sheet style chat picker (Forward to…), slid up from the bottom over the current screen. */
@Composable
fun ChatPickerSheet(title: String = "Forward to…", onDismiss: () -> Unit, onPick: (Chat) -> Unit) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    var query by remember { mutableStateOf("") }
    val chats = repo.chats
        .filter { it.joined && (it.type != ChatType.Channel || it.canPost) }
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
        )
        AnimatedVisibility(
            shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(c.groupedBackground)
                    // Swallow taps so they don't reach the dimmed background.
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                Box(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp)) {
                    T("Cancel", TgTheme.type.body, c.accent, modifier = Modifier.align(Alignment.CenterStart).fadeClickable(onClick = onDismiss))
                    T(title, TgTheme.type.headline, c.text, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
                SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(chats, key = { it.id }) { chat ->
                        Box(Modifier.background(c.cell)) {
                            Row(
                                Modifier.fillMaxWidth().iosClickable { onPick(chat) }.height(56.dp).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ChatAvatar(chat, repo, 40.dp, showOnline = false)
                                Spacer(Modifier.width(12.dp))
                                T(chat.title, TgTheme.type.body, c.text, maxLines = 1)
                            }
                            Separator(Modifier.align(Alignment.BottomStart), startPadding = 66.dp)
                        }
                    }
                    if (chats.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            T("No chats found", TgTheme.type.subheadline, c.secondaryText)
                        }
                    }
                }
            }
        }
    }
}
