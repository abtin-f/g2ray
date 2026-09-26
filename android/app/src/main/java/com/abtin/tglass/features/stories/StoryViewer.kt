package com.abtin.tglass.features.stories

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatListDate
import com.kyant.shapes.Capsule

/** Spec §31 story viewer: progress bars, tap zones, hold to pause, reply bar. */
@Composable
fun StoryViewer(startUserId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val stories = repo.stories
    var index by remember { mutableIntStateOf(stories.indexOfFirst { it.userId == startUserId }.coerceAtLeast(0)) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val story = stories.getOrNull(index) ?: run {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val user = repo.user(story.userId)

    val animatedFor = remember { intArrayOf(-1) }
    LaunchedEffect(index, paused) {
        if (animatedFor[0] != index) {
            animatedFor[0] = index
            repo.markStorySeen(story.userId)
            progress.snapTo(0f)
        }
        if (!paused) {
            val remaining = ((1f - progress.value) * 5000).toInt()
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            if (index < stories.lastIndex) index++ else nav.pop()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weightlessFill()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(story.colors.map { Color(it) }))
                .pointerInput(index) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = System.currentTimeMillis()
                        paused = true
                        val up = waitForUpOrCancellation()
                        paused = false
                        if (up != null && System.currentTimeMillis() - start < 250) {
                            if (down.position.x < size.width / 3f) {
                                if (index > 0) index--
                            } else {
                                if (index < stories.lastIndex) index++ else nav.pop()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(story.emoji, TgTheme.type.body.copy(fontSize = 140.sp, lineHeight = 160.sp))
                Spacer(Modifier.height(20.dp))
                T(story.caption, TgTheme.type.title3, Color.White, align = TextAlign.Center, weight = FontWeight.SemiBold)
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.indices.forEach { i ->
                        Box(Modifier.weight(1f).height(2.5.dp).clip(Capsule()).background(Color.White.copy(0.35f))) {
                            val f = when {
                                i < index -> 1f
                                i == index -> progress.value
                                else -> 0f
                            }
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user?.name ?: "", story.userId, 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        T(user?.name ?: "", TgTheme.type.subheadline, Color.White, weight = FontWeight.SemiBold)
                        T(formatListDate(story.date), TgTheme.type.caption1, Color.White.copy(0.7f))
                    }
                    Box(Modifier.size(40.dp).fadeClickable { nav.pop() }, contentAlignment = Alignment.Center) {
                        Icon(IosIcons.Close, Color.White, 22.dp)
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).height(44.dp).clip(Capsule()).background(Color.White.copy(0.14f)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { T("Reply privately…", TgTheme.type.body, Color.White.copy(0.7f)) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(44.dp).fadeClickable { }, contentAlignment = Alignment.Center) {
                Icon(IosIcons.Heart, Color.White, 28.dp)
            }
        }
    }
}

private fun Modifier.weightlessFill(): Modifier = this.padding(bottom = 76.dp).fillMaxHeight()
