package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.PollVotersPage
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.T
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Which poll's voters are open (chat id, message id). */
data class PollResultsTarget(val chatId: Long, val messageId: Long)

/**
 * Telegram-iOS "Poll Results": the question, then one section per option ("OPTION — 67%") with the people who
 * chose it and "Show More" when there are more (TDLib getPollVoters, public polls only).
 */
@Composable
fun PollResultsPage(target: PollResultsTarget?, onDismiss: () -> Unit) {
    BackHandler(enabled = target != null) { onDismiss() }
    val last = remember { arrayOfNulls<PollResultsTarget>(1) }
    if (target != null) last[0] = target
    AnimatedVisibility(target != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val t = target ?: last[0] ?: return@AnimatedVisibility
        PollResultsContent(t, onDismiss)
    }
}

@Composable
private fun PollResultsContent(t: PollResultsTarget, onDismiss: () -> Unit) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val c = TgTheme.colors
    val poll = repo.findMessage(t.chatId, t.messageId)?.content as? MessageContent.Poll
    val pages = remember(t) { mutableStateMapOf<Int, PollVotersPage>() }
    val loading = remember(t) { mutableStateMapOf<Int, Boolean>() }
    fun load(option: Int) {
        if (loading[option] == true) return
        loading[option] = true
        val have = pages[option]?.voterIds.orEmpty()
        repo.pollVoters(t.chatId, t.messageId, option, have.size) { page ->
            loading[option] = false
            if (page != null) pages[option] = PollVotersPage((have + page.voterIds).distinct(), page.total)
        }
    }
    LaunchedEffect(t, poll?.options?.size) {
        val p = poll ?: return@LaunchedEffect
        p.options.indices.forEach { i -> if ((p.votes.getOrNull(i) ?: 0) > 0 && pages[i] == null) load(i) }
    }
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.groupedBackground)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 32.dp),
            ) {
                if (poll == null) {
                    item { Box(Modifier.padding(32.dp)) { ActivityIndicator() } }
                } else {
                    item {
                        T(
                            poll.question, TgTheme.type.title3.copy(textDirection = TextDirection.Content), c.text,
                            weight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }
                    poll.options.forEachIndexed { i, opt ->
                        val votes = poll.votes.getOrElse(i) { 0 }
                        if (votes <= 0) return@forEachIndexed
                        item(key = "opt$i") {
                            val page = pages[i]
                            Spacer(Modifier.height(20.dp))
                            Section(header = "$opt — ${poll.percentOf(i)}%") {
                                if (page == null) {
                                    Box(Modifier.padding(16.dp)) { ActivityIndicator() }
                                } else {
                                    val more = page.total - page.voterIds.size
                                    page.voterIds.forEachIndexed { idx, id ->
                                        val user = repo.user(id)
                                        val name = user?.name ?: repo.chat(id)?.title ?: "Deleted Account"
                                        Cell(
                                            name,
                                            chevron = false,
                                            divider = idx != page.voterIds.lastIndex || more > 0,
                                            leading = { Avatar(name, id, 36.dp) },
                                            onClick = {
                                                onDismiss()
                                                if (user != null) nav.push(Route.UserProfile(id)) else nav.push(Route.Chat(id))
                                            },
                                        )
                                    }
                                    if (more > 0) {
                                        Cell(
                                            if (loading[i] == true) "Loading…" else "Show More ($more)",
                                            titleColor = c.accent, chevron = false, divider = false,
                                            onClick = { load(i) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            GlassTopBar(
                title = if (poll?.quiz == true) "Quiz Results" else "Poll Results",
                fade = c.groupedBackground,
                onBack = onDismiss,
            )
        }
    }
}
