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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.media.VideoSurface
import com.abtin.tglass.core.media.rememberVideoPlayer
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.Story
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatListDate
import com.kyant.shapes.Capsule

/** How long a photo (or demo) story stays on screen. */
private const val PHOTO_STORY_MS = 5000

/**
 * Spec §31 story viewer: per-person progress bars, tap zones, hold to pause, reply bar.
 * Demo stories are drawn as emoji on a gradient; live ones show the real photo or play the video.
 */
@Composable
fun StoryViewer(startUserId: Long, postsOf: Long = 0L, startIndex: Int = 0) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    // Profile Posts mode: only that chat's posts, starting at the tapped one.
    val postsMode = postsOf != 0L
    fun storiesFor(id: Long): List<Story> = if (postsMode) repo.profileStories(id) else repo.storiesOf(id)

    // The order of people is fixed when the viewer opens, so it doesn't reshuffle while stories become seen.
    val order = remember {
        val ids = repo.storyUsers.map { it.id }
        when {
            postsMode -> listOf(postsOf)
            startUserId in ids || repo.storiesOf(startUserId).isEmpty() -> ids
            else -> listOf(startUserId) + ids
        }
    }
    val startPos = remember { order.indexOf(if (postsMode) postsOf else startUserId).coerceAtLeast(0) }
    var userPos by remember { mutableIntStateOf(startPos) }
    var storyPos by remember {
        mutableIntStateOf(if (postsMode) startIndex else order.getOrNull(startPos)?.let { firstUnseen(repo.storiesOf(it)) } ?: 0)
    }
    var restarts by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    var videoProgress by remember { mutableFloatStateOf(0f) }
    val shownKey = remember { arrayOfNulls<Any>(1) }

    fun close() {
        if (closing) return
        closing = true
        nav.pop()
    }

    fun enterUser(pos: Int, fromEnd: Boolean) {
        val id = order.getOrNull(pos) ?: return close()
        val list = storiesFor(id)
        userPos = pos
        storyPos = if (fromEnd) (list.size - 1).coerceAtLeast(0) else firstUnseen(list)
    }

    fun next() {
        val count = order.getOrNull(userPos)?.let { storiesFor(it).size } ?: 0
        if (storyPos < count - 1) storyPos++ else enterUser(userPos + 1, fromEnd = false)
    }

    fun prev() {
        when {
            storyPos > 0 -> storyPos--
            userPos > 0 -> enterUser(userPos - 1, fromEnd = true)
            else -> restarts++
        }
    }

    val userId = order.getOrNull(userPos)
    val stories = if (userId != null) storiesFor(userId) else emptyList()

    LaunchedEffect(userId) {
        if (postsMode) return@LaunchedEffect
        if (userId != null) repo.loadStories(userId)
        order.getOrNull(userPos + 1)?.let { repo.loadStories(it) }
    }

    val story = stories.getOrNull(storyPos)
    if (story == null) {
        LaunchedEffect(userPos, stories.size) {
            when {
                userId == null -> close()
                stories.isEmpty() -> enterUser(userPos + 1, fromEnd = false) // this person's stories expired meanwhile
                else -> storyPos = stories.lastIndex
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val user = repo.user(story.userId)
    // Channels post stories too: fall back to the chat's title.
    val posterName = user?.name ?: repo.chat(story.chatId)?.title ?: ""

    // Tells the repository which story is on screen (marks it viewed).
    DisposableEffect(userPos, storyPos, story.id) {
        repo.openStory(story)
        onDispose { repo.closeStory(story) }
    }
    // Warm up the next photo while this one is shown.
    LaunchedEffect(userPos, storyPos, stories.size) {
        stories.getOrNull(storyPos + 1)?.image?.let { repo.requestImage(it) }
    }

    val image = story.image
    val isVideo = story.video != null
    val ready = when {
        isVideo -> true
        image != null -> image.fileId <= 0 || repo.filePath(image) != null
        else -> story.loaded
    }
    val posKey = Triple(userPos, storyPos, restarts)
    LaunchedEffect(posKey, paused, ready, isVideo) {
        if (shownKey[0] != posKey) {
            shownKey[0] = posKey
            progress.snapTo(0f)
            videoProgress = 0f
        }
        // Videos drive the bar themselves (see StoryVideo); photos run on a timer once they are on screen.
        if (!paused && ready && !isVideo) {
            val remaining = ((1f - progress.value) * PHOTO_STORY_MS).toInt()
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            next()
        }
    }

    val demoLook = image == null && !isVideo && story.colors.size >= 2
    val background = if (demoLook) Brush.verticalGradient(story.colors.map { Color(it) }) else SolidColor(Color(0xFF1C1C1E))

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
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .pointerInput(userPos, storyPos) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = System.currentTimeMillis()
                        paused = true
                        val up = waitForUpOrCancellation()
                        paused = false
                        if (up != null && System.currentTimeMillis() - start < 250) {
                            if (down.position.x < size.width / 3f) prev() else next()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            key(userPos, storyPos, restarts) {
                StoryContent(story, demoLook, paused, onVideoProgress = { videoProgress = it }, onVideoEnded = { next() })
            }
            if (!demoLook) {
                // Keeps the progress bars and name readable on bright photos.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.35f), Color.Transparent)))
                )
                if (story.caption.isNotBlank()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f))))
                            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp)
                    ) { T(story.caption, TgTheme.type.body, Color.White, maxLines = 6) }
                }
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.indices.forEach { i ->
                        Box(Modifier.weight(1f).height(2.5.dp).clip(Capsule()).background(Color.White.copy(0.35f))) {
                            val f = when {
                                i < storyPos -> 1f
                                i == storyPos -> if (isVideo) videoProgress else progress.value
                                else -> 0f
                            }
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(posterName, story.userId, 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        com.abtin.tglass.core.emoji.EmojiText(posterName, TgTheme.type.subheadline, Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                        if (story.date > 0) T(formatListDate(story.date), TgTheme.type.caption1, Color.White.copy(0.7f))
                    }
                    Box(Modifier.size(40.dp).fadeClickable { close() }, contentAlignment = Alignment.Center) {
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

/** What fills the story card: demo emoji, the photo, the video, or a loading / unsupported state. */
@Composable
private fun StoryContent(story: Story, demoLook: Boolean, paused: Boolean, onVideoProgress: (Float) -> Unit, onVideoEnded: () -> Unit) {
    val repo = LocalRepository.current
    val image = story.image
    val video = story.video
    when {
        video != null -> StoryVideo(video, image, paused, onVideoProgress, onVideoEnded)
        image != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TgImage(image, Modifier.fillMaxSize(), maxPx = 2048, contentScale = ContentScale.Crop)
            if (image.fileId > 0 && repo.filePath(image) == null) ActivityIndicator(28.dp, Color.White)
        }
        demoLook || story.emoji.isNotEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(story.emoji, TgTheme.type.body.copy(fontSize = 140.sp, lineHeight = 160.sp))
            Spacer(Modifier.height(20.dp))
            T(story.caption, TgTheme.type.title3, Color.White, align = TextAlign.Center, weight = FontWeight.SemiBold)
        }
        !story.loaded -> ActivityIndicator(28.dp, Color.White)
        else -> T(
            "This story can't be shown.",
            TgTheme.type.body,
            Color.White.copy(0.8f),
            modifier = Modifier.padding(horizontal = 32.dp),
            align = TextAlign.Center,
        )
    }
}

/** Plays a video story once with sound; reports playback progress and when it ends. */
@Composable
private fun StoryVideo(video: ImageRef, cover: ImageRef?, paused: Boolean, onProgress: (Float) -> Unit, onEnded: () -> Unit) {
    val repo = LocalRepository.current
    val path = repo.filePath(video)
    LaunchedEffect(video.fileId, path) { if (path == null) repo.requestImage(video) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        if (path == null) {
            if (cover != null) TgImage(cover, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ActivityIndicator(28.dp, Color.White)
        } else {
            val player = rememberVideoPlayer(path, loop = false, muted = false)
            val ended by rememberUpdatedState(onEnded)
            val progressCallback by rememberUpdatedState(onProgress)
            var firstFrame by remember(player) { mutableStateOf(false) }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) ended()
                    }

                    override fun onRenderedFirstFrame() {
                        firstFrame = true
                    }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener) }
            }
            LaunchedEffect(player, paused) { if (paused) player.pause() else player.play() }
            // Frame-synced so the progress bar moves smoothly with the video.
            LaunchedEffect(player) {
                while (true) {
                    withFrameNanos { }
                    val duration = player.duration
                    if (duration > 0) progressCallback((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
                }
            }
            // Fill the card like ContentScale.Crop (a TextureView would otherwise stretch the picture).
            val vw = video.width
            val vh = video.height
            val surface = if (vw > 0 && vh > 0) {
                val scale = maxOf(maxWidth.value / vw, maxHeight.value / vh)
                Modifier.requiredSize((vw * scale).dp, (vh * scale).dp)
            } else Modifier.fillMaxSize()
            VideoSurface(player, surface)
            if (!firstFrame && cover != null) TgImage(cover, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun firstUnseen(list: List<Story>): Int = list.indexOfFirst { !it.seen }.coerceAtLeast(0)

private fun Modifier.weightlessFill(): Modifier = this.padding(bottom = 76.dp).fillMaxHeight()
