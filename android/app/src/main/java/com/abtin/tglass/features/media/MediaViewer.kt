package com.abtin.tglass.features.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.abtin.tglass.core.design.ProvideDarkColors
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.Sharing
import com.abtin.tglass.core.media.VideoState
import com.abtin.tglass.core.media.VideoSurface
import com.abtin.tglass.core.media.isReleasedSafely
import com.abtin.tglass.core.media.rememberVideoPlayer
import com.abtin.tglass.core.media.rememberVideoState
import com.abtin.tglass.core.media.togglePlay
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.MediaKind
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.senderName
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.formatTime
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SeekStepMs = 10_000L
private val Speeds = listOf(0.5f, 1f, 1.5f, 2f)

/**
 * Spec §26: full-screen media viewer like Telegram iOS 26. Swipe left/right through the chat's photos and videos,
 * pinch/double-tap zoom, swipe down to dismiss; videos get the iOS glass control bar (scrubber, time, mute, speed,
 * double-tap ±10 s). Share, Save to Gallery, Forward and Delete act on the real file/message.
 *
 * Crash safety: exactly one ExoPlayer exists — for the page being looked at — created under a key of that message
 * and released when the page changes or the viewer closes; surfaces detach before release; every player access is
 * guarded; nothing inside the pager (which is recorded by the backdrop layer) draws glass.
 */
@Composable
fun MediaViewer(chatId: Long, messageId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    LaunchedEffect(chatId) { repo.loadSharedMedia(chatId, MediaKind.Media) }
    val items by remember(chatId, messageId) { derivedStateOf { viewerItems(repo, chatId, messageId) } }
    var forwarding by remember { mutableStateOf<Message?>(null) }

    ProvideDarkColors {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                GlassIconButton(IosIcons.ChevronLeft, { nav.pop() }, Modifier.statusBarsPadding().padding(12.dp), iconSize = 20.dp, tint = Color.White)
            }
        } else {
            ViewerContent(items, messageId, onForward = { forwarding = it })
        }
    }
    forwarding?.let { fm ->
        // Same entry point as the chat screen's Forward (ChatPickerSheet with several targets + comment).
        ForwardSheet(fromChatId = fm.chatId, messageIds = listOf(fm.id), onDismiss = { forwarding = null }, onDone = { forwarding = null })
    }
}

/** Accumulating "« 20 seconds" feedback of double-tap seeking. */
private class SeekFeedback(val forward: Boolean, val seconds: Int, val tick: Int)

@Composable
private fun ViewerContent(items: List<Message>, initialId: Long, onForward: (Message) -> Unit) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The list grows as shared media loads (older items are inserted in front), so the page follows the message id.
    var currentId by remember { mutableLongStateOf(initialId) }
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0)) { items.size }
    val latestItems by rememberUpdatedState(items)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { p -> latestItems.getOrNull(p)?.let { currentId = it.id } }
    }
    val ids = remember(items) { items.map { it.id } }
    LaunchedEffect(ids) {
        val i = ids.indexOf(currentId)
        if (i >= 0 && i != pager.currentPage && !pager.isScrollInProgress) pager.scrollToPage(i)
    }
    val page = pager.currentPage.coerceIn(0, items.lastIndex)
    val current = items[page]
    val photo = current.content as? MessageContent.Photo

    val dismiss = remember { Animatable(0f) }
    val dragging by remember { derivedStateOf { dismiss.value != 0f } }
    var closing by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var captionExpanded by remember(current.id) { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        nav.pop()
    }

    // ---- The one video player (current page only) ----
    val videoRef = photo?.takeIf { it.video }?.videoFile
    val videoPath = videoRef?.let { repo.filePath(it) }
    LaunchedEffect(videoRef, videoPath) { if (videoRef != null && videoPath == null) repo.requestImage(videoRef) }
    val loop = photo?.loop == true
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    val player: ExoPlayer? = if (videoPath != null && !closing) {
        key(current.id, videoPath) { rememberVideoPlayer(videoPath, loop = loop, muted = loop || muted) }
    } else null
    val video: VideoState? = player?.let { rememberVideoState(it) }
    LaunchedEffect(player, speed) {
        val p = player ?: return@LaunchedEffect
        if (!p.isReleasedSafely) p.setPlaybackSpeed(speed)
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player?.takeIf { !it.isReleasedSafely }?.pause()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(current.id) { speedMenu = false }

    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var lastFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    LaunchedEffect(seekFeedback?.tick) {
        if (seekFeedback != null) {
            delay(750)
            seekFeedback = null
        }
    }
    fun seekBy(forward: Boolean): Boolean {
        val p = player ?: return false
        if (p.isReleasedSafely || loop) return false
        val d = p.duration
        val target = (p.currentPosition + if (forward) SeekStepMs else -SeekStepMs).coerceAtLeast(0L)
        p.seekTo(if (d > 0) target.coerceAtMost(d) else target)
        val prev = seekFeedback
        val secs = if (prev != null && prev.forward == forward) prev.seconds + 10 else 10
        seekFeedback = SeekFeedback(forward, secs, (prev?.tick ?: 0) + 1).also { lastFeedback = it }
        return true
    }

    // ---- Actions ----

    /** The file behind a photo/video message and whether it is a video. */
    fun fileOf(m: Message): Pair<ImageRef, Boolean>? {
        val p = m.content as? MessageContent.Photo ?: return null
        return if (p.video || p.loop) p.videoFile?.let { it to true } else p.image?.let { it to false }
    }

    /** Local path of the message's file, or null (and a download is started) while it is not available. */
    fun readyPath(m: Message): Pair<String, Boolean>? {
        val (ref, isVideo) = fileOf(m) ?: run {
            toast.show(if (repo.isLive) "This media has no file" else "Sample media has no file", Icons.Rounded.ErrorOutline)
            return null
        }
        val path = repo.filePath(ref)
        if (path == null) {
            repo.requestImage(ref)
            toast.show("Downloading… try again in a moment", Icons.Rounded.ErrorOutline)
            return null
        }
        return path to isVideo
    }

    fun doSave(path: String, isVideo: Boolean) {
        scope.launch {
            val ok = Sharing.saveToGallery(context, path, isVideo)
            if (ok) toast.show(if (isVideo) "Video saved to Gallery" else "Photo saved to Gallery")
            else toast.show("Couldn't save to the gallery", Icons.Rounded.ErrorOutline)
        }
    }

    var pendingSave by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val p = pendingSave
        pendingSave = null
        if (granted && p != null) doSave(p.first, p.second)
        else if (!granted) toast.show("Allow storage access to save to the gallery", Icons.Rounded.ErrorOutline)
    }

    fun save(m: Message) {
        val (path, isVideo) = readyPath(m) ?: return
        if (Sharing.saveNeedsPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = path to isVideo
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        doSave(path, isVideo)
    }

    fun share(m: Message) {
        val (path, isVideo) = readyPath(m) ?: return
        scope.launch {
            val ok = Sharing.shareFile(context, path, Sharing.mimeOf(path, if (isVideo) "video/mp4" else "image/jpeg"))
            if (!ok) toast.show("Couldn't share this file", Icons.Rounded.ErrorOutline)
        }
    }

    fun canDelete(m: Message): Boolean {
        val chat = repo.chat(m.chatId) ?: return false
        return chat.type != ChatType.Channel || chat.canPost
    }

    fun confirmDelete(m: Message) {
        val chat = repo.chat(m.chatId) ?: return
        val isVideo = (m.content as? MessageContent.Photo)?.video == true
        val noun = if (isVideo) "video" else "photo"
        val delete: (Boolean) -> Unit = { everyone ->
            repo.deleteMessages(m.chatId, setOf(m.id), forEveryone = everyone)
            close()
        }
        // Same rules as the chat screen: private chats and your own messages can be deleted for everyone.
        val canRevoke = repo.isLive && chat.type != ChatType.Saved &&
            (chat.type == ChatType.Private || chat.type == ChatType.Bot || m.outgoing)
        val actions = if (canRevoke) listOf(
            SheetAction(if (chat.type == ChatType.Private) "Delete for Me and ${chat.title.substringBefore(' ')}" else "Delete for Everyone", destructive = true) { delete(true) },
            SheetAction("Delete for Me", destructive = true) { delete(false) },
        ) else listOf(
            SheetAction(if (isVideo) "Delete Video" else "Delete Photo", destructive = true) { delete(chat.type == ChatType.Channel) },
        )
        sheet.show(SheetRequest(title = if (canRevoke) "Delete this $noun?" else null, actions = actions))
    }

    fun more(m: Message) {
        sheet.show(SheetRequest(actions = listOfNotNull(
            SheetAction("Save to Gallery") { save(m) },
            SheetAction("Share") { share(m) },
            SheetAction("Forward") { onForward(m) },
            if (canDelete(m)) SheetAction("Delete", destructive = true) { confirmDelete(m) } else null,
        )))
    }

    BackHandler(enabled = speedMenu) { speedMenu = false }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // Background fades while the picture is dragged down (read in the draw phase only).
                    val a = (1f - abs(dismiss.value) / (size.height * 0.6f)).coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = a))
                },
        ) {
            // Everything in this box is recorded for the glass controls: no glass may be drawn inside it (rule 2).
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                CompositionLocalProvider(LocalBackdrop provides null) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !zoomed && !dragging,
                        key = { i -> latestItems.getOrNull(i)?.id ?: (Long.MIN_VALUE + i) },
                    ) { i ->
                        val m = items.getOrNull(i)
                        val p = m?.content as? MessageContent.Photo
                        if (m != null && p != null) {
                            val isCurrent = m.id == current.id
                            MediaPage(
                                photo = p,
                                isCurrent = isCurrent,
                                player = if (isCurrent) player else null,
                                video = if (isCurrent) video else null,
                                dismissY = { dismiss.value },
                                onTap = { chrome = !chrome; speedMenu = false },
                                onSideDoubleTap = { forward -> isCurrent && seekBy(forward) },
                                onZoom = { zoomed = it },
                                onDrag = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } },
                                onDragEnd = {
                                    val h = context.resources.displayMetrics.heightPixels.toFloat()
                                    if (abs(dismiss.value) > h * 0.12f) {
                                        scope.launch {
                                            dismiss.animateTo(if (dismiss.value > 0) h else -h, tween(180))
                                            close()
                                        }
                                    } else {
                                        scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ---- Overlays (outside the recorded layer) ----

            if (video != null && video.buffering && !dragging) {
                ActivityIndicator(36.dp, Color.White, Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = seekFeedback != null,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(250)),
                modifier = Modifier.fillMaxSize(),
            ) {
                lastFeedback?.let { SeekFeedbackOverlay(it) }
            }

            AnimatedVisibility(
                visible = chrome && !dragging,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Top bar: back, sender + date, more.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.45f), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(IosIcons.ChevronLeft, { close() }, iconSize = 20.dp, tint = Color.White)
                        Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.height(44.dp)) {
                                Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    T(
                                        repo.senderName(current),
                                        TgTheme.type.subheadline.copy(textDirection = TextDirection.Content),
                                        Color.White,
                                        weight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    T("${formatDay(current.date)} at ${formatTime(current.date)}", TgTheme.type.caption1, Color.White.copy(0.7f), maxLines = 1)
                                }
                            }
                        }
                        GlassIconButton(TgIcons.PiMore, { more(current) }, tint = Color.White)
                    }

                    // Big play button while a video is paused.
                    if (player != null && video != null && !loop && !video.playing && !video.buffering) {
                        GlassIconButton(IosIcons.Play, { player.togglePlay() }, Modifier.align(Alignment.Center), size = 68.dp, iconSize = 30.dp, tint = Color.White)
                    }

                    // Bottom: caption, video bar, share / counter / forward.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f))))
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
                    ) {
                        photo?.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                            T(
                                caption,
                                TgTheme.type.body.copy(textDirection = TextDirection.Content),
                                Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .padding(bottom = 12.dp)
                                    .clickable(remember { MutableInteractionSource() }, null) { captionExpanded = !captionExpanded },
                                maxLines = if (captionExpanded) 30 else 3,
                            )
                        }
                        if (player != null && video != null && !loop) {
                            VideoBar(
                                player = player,
                                state = video,
                                muted = muted,
                                speed = speed,
                                onMute = { muted = !muted },
                                onSpeed = { speedMenu = !speedMenu },
                            )
                            Spacer(Modifier.height(10.dp))
                        } else if (videoRef != null && videoPath == null && !loop) {
                            // Still downloading: a disabled bar keeps the layout from jumping.
                            DownloadingBar(repo.fileProgress(videoRef), photo?.duration ?: 0)
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(TgIcons.IcNavShare, { share(current) }, tint = Color.White)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (items.size > 1) {
                                    GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.height(36.dp)) {
                                        T(
                                            "${page + 1} of ${items.size}",
                                            TgTheme.type.subheadline,
                                            Color.White,
                                            weight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                }
                            }
                            GlassIconButton(TgIcons.CtxForward, { onForward(current) }, tint = Color.White)
                        }
                    }
                }
            }

            if (speedMenu && player != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(remember { MutableInteractionSource() }, null) { speedMenu = false },
                )
                SpeedMenu(
                    speed = speed,
                    onPick = { s -> speed = s; speedMenu = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 128.dp),
                )
            }
        }
    }
}

/** Photos and videos of the chat in chronological order: loaded history + shared media + the opened message. */
private fun viewerItems(repo: TelegramRepository, chatId: Long, messageId: Long): List<Message> {
    val byId = LinkedHashMap<Long, Message>()
    fun add(m: Message) {
        val p = m.content as? MessageContent.Photo ?: return
        // GIF-style loops only when opened directly (they are not part of the swipeable media).
        if (p.loop && m.id != messageId) return
        byId[m.id] = m
    }
    repo.sharedMedia(chatId, MediaKind.Media).forEach { add(it) }
    repo.messages(chatId).forEach { add(it) }
    repo.findMessage(chatId, messageId)?.let { add(it) }
    return byId.values.sortedWith(compareBy<Message>({ it.date }, { it.id }))
}

/** Picture/video size numbers kept outside of snapshot state (read by gesture code). */
private class PageGeometry {
    var pageW = 0f
    var pageH = 0f
    var contentW = 0f
    var contentH = 0f
}

/**
 * One zoomable photo/video page. Only the current page gets a [player]. Draws no glass (it is inside the recorded
 * backdrop layer); the video controls live in the viewer's overlay.
 */
@Composable
private fun MediaPage(
    photo: MessageContent.Photo,
    isCurrent: Boolean,
    player: ExoPlayer?,
    video: VideoState?,
    dismissY: () -> Float,
    onTap: () -> Unit,
    onSideDoubleTap: (forward: Boolean) -> Boolean,
    onZoom: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val sideTap by rememberUpdatedState(onSideDoubleTap)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val isVideo = photo.video
    val isVideoState by rememberUpdatedState(isVideo)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomJob = remember { arrayOfNulls<Job>(1) }
    val geo = remember { PageGeometry() }
    val zoomed = scale > 1.01f
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            zoomJob[0]?.cancel()
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(isCurrent, zoomed) { if (isCurrent) onZoom(zoomed) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = ((geo.contentW * s - geo.pageW) / 2f).coerceAtLeast(0f)
        val maxY = ((geo.contentH * s - geo.pageH) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun animateZoom(toScale: Float, toOffset: Offset) {
        zoomJob[0]?.cancel()
        val s0 = scale
        val o0 = offset
        zoomJob[0] = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { f, _ ->
                scale = s0 + (toScale - s0) * f
                offset = lerp(o0, toOffset, f)
            }
        }
    }

    // Videos: download (with progress) while this page is looked at.
    val videoFile = photo.videoFile?.takeIf { isVideo }
    val videoPath = videoFile?.let { repo.filePath(it) }
    LaunchedEffect(videoFile, videoPath, isCurrent) { if (isCurrent && videoFile != null && videoPath == null) repo.requestImage(videoFile) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { pos ->
                        val w = size.width.toFloat()
                        val side = when {
                            pos.x < w * 0.3f -> -1
                            pos.x > w * 0.7f -> 1
                            else -> 0
                        }
                        // Videos: double-tap near an edge seeks ±10 s (iOS); elsewhere (and photos) toggles zoom.
                        val seeked = isVideoState && side != 0 && scale <= 1.01f && sideTap(side > 0)
                        if (seeked) {
                            // handled as a seek
                        } else if (scale > 1.01f) {
                            animateZoom(1f, Offset.Zero)
                        } else {
                            val target = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            animateZoom(target, clamp((pos - center) * (1f - target), target))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Pinch to zoom around the fingers; one-finger pan only while zoomed, so swipes still reach the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var touched = false
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            if (!touched) {
                                zoomJob[0]?.cancel()
                                touched = true
                            }
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(0.8f, 6f)
                            var t = offset
                            if (centroid.isSpecified && scale > 0f) {
                                val c = centroid - Offset(size.width / 2f, size.height / 2f)
                                t = c - (c - t) * (newScale / scale)
                            }
                            t += pan
                            scale = newScale
                            offset = if (newScale > 1f) clamp(t, newScale) else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (touched) {
                        if (scale < 1.05f) animateZoom(1f, Offset.Zero)
                        else if (scale > 4f) animateZoom(4f, clamp(offset * (4f / scale), 4f))
                    }
                }
            }
            .pointerInput(zoomed) {
                if (!zoomed) detectVerticalDragGestures(
                    onDragEnd = { dragEnd() },
                    onDragCancel = { dragEnd() },
                ) { change, dy ->
                    change.consume()
                    drag(dy)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val pageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val aspect = contentAspect(photo, video)
        val fitW = if (pageW / pageH > aspect) pageH * aspect else pageW
        val fitH = fitW / aspect
        geo.pageW = pageW
        geo.pageH = pageH
        geo.contentW = fitW
        geo.contentH = fitH
        val (a, b) = avatarColors(photo.seed.toLong())
        Box(
            Modifier
                .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                .graphicsLayer {
                    val dy = if (isCurrent) dismissY() else 0f
                    val shrink = 1f - (abs(dy) / (pageH * 3f)).coerceIn(0f, 0.2f)
                    scaleX = scale * shrink
                    scaleY = scale * shrink
                    translationX = offset.x
                    translationY = offset.y + dy
                }
                .then(if (photo.image == null) Modifier.background(Brush.linearGradient(listOf(a, b))) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.image != null) TgImage(photo.image, Modifier.matchParentSize(), maxPx = 2048, contentScale = ContentScale.Fit)
            else if (videoFile == null) T(photo.emoji, TgTheme.type.body.copy(fontSize = 120.sp, lineHeight = 140.sp))
            if (player != null && video != null) {
                VideoSurface(player, Modifier.matchParentSize())
                // Cover until the first frame is drawn, so switching pages never flashes black.
                if (!video.firstFrame && photo.image != null) TgImage(photo.image, Modifier.matchParentSize(), maxPx = 2048, contentScale = ContentScale.Fit)
            } else if (videoFile != null && videoPath == null && isCurrent) {
                DownloadRing(repo.fileProgress(videoFile))
            }
        }
    }
}

/** Width / height of what the page shows: the decoded video, else the file's size, else the message's aspect. */
private fun contentAspect(photo: MessageContent.Photo, video: VideoState?): Float {
    val decoded = video?.videoAspect ?: 0f
    if (decoded > 0f) return decoded.coerceIn(0.1f, 10f)
    val ref = if (photo.video) photo.videoFile?.takeIf { it.width > 0 && it.height > 0 } else null
    val img = ref ?: photo.image?.takeIf { it.width > 0 && it.height > 0 }
    if (img != null) return (img.width.toFloat() / img.height).coerceIn(0.1f, 10f)
    return photo.aspect.takeIf { it > 0f }?.coerceIn(0.1f, 10f) ?: 1f
}

/** Downloading: ring with the percentage, like Telegram's media overlay (plain drawing, no glass). */
@Composable
private fun DownloadRing(progress: Float) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp)) {
            drawArc(
                Color.White, -90f, 360f * progress.coerceIn(0.04f, 1f), false,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        T("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", TgTheme.type.caption1, Color.White, weight = FontWeight.SemiBold)
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun speedLabel(s: Float): String = when (s) {
    0.5f -> "0.5×"
    1f -> "1×"
    1.5f -> "1.5×"
    2f -> "2×"
    else -> "${s}×"
}

/** iOS 26 video bar: play/pause, elapsed, scrubber (seeks while dragging), remaining, mute, speed. */
@Composable
private fun VideoBar(
    player: ExoPlayer,
    state: VideoState,
    muted: Boolean,
    speed: Float,
    onMute: () -> Unit,
    onSpeed: () -> Unit,
) {
    var scrub by remember(player) { mutableStateOf<Float?>(null) }
    var resumeAfterScrub by remember(player) { mutableStateOf(false) }
    val duration = state.durationMs
    val shownMs = scrub?.let { (it * duration).toLong() } ?: state.positionMs
    val fraction = if (duration > 0) (shownMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).fadeClickable { player.togglePlay() }, contentAlignment = Alignment.Center) {
                Icon(if (state.playing) IosIcons.Pause else IosIcons.Play, Color.White, 22.dp)
            }
            T(formatMs(shownMs), TgTheme.type.caption1.copy(fontSize = 12.sp), Color.White, weight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 34.dp))
            Scrubber(
                fraction = fraction,
                active = scrub != null,
                onStart = {
                    if (!player.isReleasedSafely) {
                        resumeAfterScrub = player.playWhenReady
                        player.pause()
                        runCatching { player.setSeekParameters(SeekParameters.CLOSEST_SYNC) }
                    }
                },
                onMove = { f ->
                    scrub = f
                    if (!player.isReleasedSafely && duration > 0) player.seekTo((f * duration).toLong())
                },
                onEnd = {
                    val f = scrub
                    scrub = null
                    if (!player.isReleasedSafely) {
                        runCatching { player.setSeekParameters(SeekParameters.EXACT) }
                        if (f != null && duration > 0) player.seekTo((f * duration).toLong())
                        if (resumeAfterScrub) player.play()
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            T(
                "-" + formatMs((duration - shownMs).coerceAtLeast(0L)),
                TgTheme.type.caption1.copy(fontSize = 12.sp),
                Color.White.copy(0.75f),
                weight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 38.dp),
            )
            Box(Modifier.size(38.dp).clip(CircleShape).fadeClickable(onClick = onMute), contentAlignment = Alignment.Center) {
                Icon(if (muted) MediaIcons.SpeakerOff else MediaIcons.SpeakerOn, Color.White, 22.dp)
            }
            Box(
                Modifier.height(38.dp).widthIn(min = 38.dp).clip(Capsule()).fadeClickable(onClick = onSpeed).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(speedLabel(speed), TgTheme.type.subheadline, Color.White, weight = FontWeight.Bold)
            }
        }
    }
}

/** Placeholder bar while the video file downloads. */
@Composable
private fun DownloadingBar(progress: Float, durationSec: Int) {
    GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ActivityIndicator(18.dp, Color.White)
            Spacer(Modifier.width(10.dp))
            T("Downloading… ${(progress.coerceIn(0f, 1f) * 100).toInt()}%", TgTheme.type.footnote, Color.White, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (durationSec > 0) T(formatMs(durationSec * 1000L), TgTheme.type.footnote, Color.White.copy(0.7f))
        }
    }
}

/** Thin track with a round thumb; the thumb grows while dragged. Reports 0..1 while the finger moves. */
@Composable
private fun Scrubber(
    fraction: Float,
    active: Boolean,
    onStart: () -> Unit,
    onMove: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    BoxWithConstraints(
        modifier
            .height(40.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    start()
                    move((down.position.x / w).coerceIn(0f, 1f))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            move((change.position.x / w).coerceIn(0f, 1f))
                        }
                    } finally {
                        end()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val thumb = if (active) 18.dp else 12.dp
        val track = if (active) 6.dp else 4.dp
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = with(density) { thumb.toPx() }
        Box(Modifier.fillMaxWidth().height(track).clip(Capsule()).background(Color.White.copy(0.3f)))
        Box(Modifier.fillMaxWidth(fraction).height(track).clip(Capsule()).background(Color.White))
        Box(
            Modifier
                .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                .size(thumb)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Playback speed menu (glass, above the video bar). */
@Composable
private fun SpeedMenu(speed: Float, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
    GlassBox(onClick = null, shape = RoundedRectangle(22.dp), modifier = modifier.width(200.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            T("Playback Speed", TgTheme.type.footnote, Color.White.copy(0.6f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            Speeds.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().height(44.dp).fadeClickable { onPick(s) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(26.dp)) { if (s == speed) Icon(IosIcons.Checkmark, Color.White, 18.dp) }
                    T(if (s == 1f) "Normal" else speedLabel(s), TgTheme.type.body, Color.White, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** iOS-style double-tap seek feedback: a soft half-disc on that side with chevrons and the accumulated seconds. */
@Composable
private fun SeekFeedbackOverlay(fb: SeekFeedback) {
    Box(Modifier.fillMaxSize()) {
        val shape = if (fb.forward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
        Box(
            Modifier
                .align(if (fb.forward) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(0.36f)
                .fillMaxHeight(0.7f)
                .clip(shape)
                .background(Color.White.copy(0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(if (fb.forward) MediaIcons.SeekForward else MediaIcons.SeekBack, Color.White, 30.dp)
                Spacer(Modifier.height(6.dp))
                T("${fb.seconds} seconds", TgTheme.type.footnote, Color.White, weight = FontWeight.SemiBold)
            }
        }
    }
}
