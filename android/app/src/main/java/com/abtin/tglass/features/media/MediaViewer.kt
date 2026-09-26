package com.abtin.tglass.features.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.Sharing
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
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.formatDuration
import com.abtin.tglass.ui.components.formatTime
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Spec §26: full-screen media viewer. Swipe left/right through the chat's photos and videos, pinch/double-tap
 * zoom, swipe down to dismiss; Share, Save to Gallery, Forward and Delete act on the real file/message.
 */
@Composable
fun MediaViewer(chatId: Long, messageId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(chatId) { repo.loadSharedMedia(chatId, MediaKind.Media) }

    val items = viewerItems(repo, chatId, messageId)
    if (items.isEmpty()) return

    // The list grows as shared media loads (older items are inserted in front), so the page follows the message id.
    var currentId by remember { mutableLongStateOf(messageId) }
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == messageId }.coerceAtLeast(0)) { items.size }
    val latestItems by rememberUpdatedState(items)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { p -> latestItems.getOrNull(p)?.let { currentId = it.id } }
    }
    val ids = items.map { it.id }
    LaunchedEffect(ids) {
        val i = ids.indexOf(currentId)
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }
    val current = items.getOrNull(pager.currentPage) ?: items.first()

    val dismiss = remember { Animatable(0f) }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    val bgAlpha = (1f - abs(dismiss.value) / 1200f).coerceIn(0f, 1f)

    // ---- Actions ----

    /** The file behind a photo/video message and whether it is a video. */
    fun fileOf(m: Message): Pair<ImageRef, Boolean>? {
        val p = m.content as? MessageContent.Photo ?: return null
        return if (p.video || p.loop) p.videoFile?.let { it to true } else p.image?.let { it to false }
    }

    /** Local path of the message's file, or null (and a download is started) while it is not available. */
    fun readyPath(m: Message): Pair<String, Boolean>? {
        val (ref, video) = fileOf(m) ?: run {
            toast.show(if (repo.isLive) "This media has no file" else "Sample media has no file", Icons.Rounded.ErrorOutline)
            return null
        }
        val path = repo.filePath(ref)
        if (path == null) {
            repo.requestImage(ref)
            toast.show("Downloading… try again in a moment", Icons.Rounded.ErrorOutline)
            return null
        }
        return path to video
    }

    fun doSave(path: String, video: Boolean) {
        scope.launch {
            val ok = Sharing.saveToGallery(context, path, video)
            if (ok) toast.show(if (video) "Video saved to Gallery" else "Photo saved to Gallery")
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
        val (path, video) = readyPath(m) ?: return
        if (Sharing.saveNeedsPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = path to video
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        doSave(path, video)
    }

    fun share(m: Message) {
        val (path, video) = readyPath(m) ?: return
        scope.launch {
            val ok = Sharing.shareFile(context, path, Sharing.mimeOf(path, if (video) "video/mp4" else "image/jpeg"))
            if (!ok) toast.show("Couldn't share this file", Icons.Rounded.ErrorOutline)
        }
    }

    fun canDelete(m: Message): Boolean {
        val chat = repo.chat(m.chatId) ?: return false
        return chat.type != ChatType.Channel || chat.canPost
    }

    fun confirmDelete(m: Message) {
        val chat = repo.chat(m.chatId) ?: return
        val video = (m.content as? MessageContent.Photo)?.video == true
        val noun = if (video) "video" else "photo"
        val delete: (Boolean) -> Unit = { everyone ->
            repo.deleteMessages(m.chatId, setOf(m.id), forEveryone = everyone)
            nav.pop()
        }
        // Same rules as the chat screen: private chats and your own messages can be deleted for everyone.
        val canRevoke = repo.isLive && chat.type != ChatType.Saved &&
            (chat.type == ChatType.Private || chat.type == ChatType.Bot || m.outgoing)
        val actions = if (canRevoke) listOf(
            SheetAction(if (chat.type == ChatType.Private) "Delete for Me and ${chat.title.substringBefore(' ')}" else "Delete for Everyone", destructive = true) { delete(true) },
            SheetAction("Delete for Me", destructive = true) { delete(false) },
        ) else listOf(
            SheetAction(if (video) "Delete Video" else "Delete Photo", destructive = true) { delete(chat.type == ChatType.Channel) },
        )
        sheet.show(SheetRequest(title = if (canRevoke) "Delete this $noun?" else null, actions = actions))
    }

    fun more(m: Message) {
        sheet.show(SheetRequest(actions = listOfNotNull(
            SheetAction("Save to Gallery") { save(m) },
            SheetAction("Forward") { forwarding = m },
            if (canDelete(m)) SheetAction("Delete", destructive = true) { confirmDelete(m) } else null,
        )))
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgAlpha))) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                userScrollEnabled = !zoomed && dismiss.value == 0f,
                key = { page -> items.getOrNull(page)?.id ?: page.toLong() },
            ) { page ->
                val m = items.getOrNull(page)
                val photo = m?.content as? MessageContent.Photo
                if (m != null && photo != null) {
                    MediaPage(
                        photo = photo,
                        active = page == pager.currentPage,
                        chrome = chrome,
                        dismissY = { dismiss.value },
                        onTap = { chrome = !chrome },
                        onZoom = { zoomed = it },
                        onDrag = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } },
                        onDragEnd = {
                            if (abs(dismiss.value) > 250f) nav.pop() else scope.launch { dismiss.animateTo(0f) }
                        },
                    )
                }
            }
            if (chrome) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(IosIcons.Close, { nav.pop() }, iconSize = 20.dp)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(repo.senderName(current), TgTheme.type.headline, Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                        val position = if (items.size > 1) "${pager.currentPage + 1} of ${items.size} · " else ""
                        T("$position${formatDay(current.date)} at ${formatTime(current.date)}", TgTheme.type.footnote, Color.White.copy(0.7f), maxLines = 1)
                    }
                    GlassIconButton(TgIcons.PiMore, { more(current) })
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    GlassIconButton(TgIcons.IcNavShare, { share(current) })
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(TgIcons.CtxForward, { forwarding = current })
                }
            }
        }
        forwarding?.let { fm ->
            ChatPickerSheet(onDismiss = { forwarding = null }) { target ->
                forwarding = null
                repo.forward(fm.chatId, listOf(fm.id), target.id)
                toast.show("Forwarded to ${target.title}")
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

/** One zoomable photo/video page. Only the [active] page plays video. */
@Composable
private fun MediaPage(
    photo: MessageContent.Photo,
    active: Boolean,
    chrome: Boolean,
    dismissY: () -> Float,
    onTap: () -> Unit,
    onZoom: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val repo = LocalRepository.current
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1f
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(active, zoomed) { if (active) onZoom(zoomed) }

    val (a, b) = avatarColors(photo.seed.toLong())
    // Videos: download (with progress) and then play with ExoPlayer — only on the page being looked at.
    val videoFile = photo.videoFile?.takeIf { photo.video }
    val videoPath = videoFile?.let { repo.filePath(it) }
    LaunchedEffect(videoFile, videoPath, active) { if (active && videoFile != null && videoPath == null) repo.requestImage(videoFile) }
    val player = if (active && videoPath != null) com.abtin.tglass.core.media.rememberVideoPlayer(videoPath, loop = photo.loop, muted = photo.loop) else null
    val video = player?.let { com.abtin.tglass.core.media.rememberVideoState(it) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            }
            .pointerInput(Unit) {
                // Pinch to zoom; one-finger pan only while zoomed, so horizontal swipes still reach the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
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
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(photo.aspect.coerceIn(0.5f, 2f))
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + if (active) dismissY() else 0f
                }
                .then(if (photo.image == null) Modifier.background(Brush.linearGradient(listOf(a, b))) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.image != null) com.abtin.tglass.ui.components.TgImage(photo.image, Modifier.matchParentSize(), maxPx = 2048, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else if (videoFile == null) T(photo.emoji, TgTheme.type.body.copy(fontSize = 120.sp, lineHeight = 140.sp))
            if (player != null) {
                com.abtin.tglass.core.media.VideoSurface(player, Modifier.matchParentSize())
                if (video?.playing == false) {
                    GlassIconButton(IosIcons.Play, { player.play() }, size = 64.dp, iconSize = 30.dp, tint = Color.White)
                }
            } else if (videoFile != null && active) {
                // Downloading: ring with the percentage, like Telegram's media overlay.
                val progress = repo.fileProgress(videoFile)
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
                        drawArc(
                            Color.White, -90f, 360f * progress.coerceAtLeast(0.04f), false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                    T("${(progress * 100).toInt()}%", TgTheme.type.caption1, Color.White, weight = FontWeight.SemiBold)
                }
            }
        }
        if (chrome) {
            // Caption and video controls sit above the viewer's bottom buttons.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 72.dp)) {
                photo.caption?.let { T(it, TgTheme.type.body, Color.White) }
                if (player != null && video != null && !photo.loop) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(if (video.playing) IosIcons.Pause else IosIcons.Play, { player.togglePlay() }, size = 40.dp, iconSize = 20.dp, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        T(formatDuration((video.positionMs / 1000).toInt()), TgTheme.type.caption1, Color.White)
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            com.abtin.tglass.ui.components.IOSSlider(
                                value = if (video.durationMs > 0) video.positionMs.toFloat() / video.durationMs else 0f,
                                onValueChange = { f -> if (video.durationMs > 0) player.seekTo((f * video.durationMs).toLong()) },
                                range = 0f..1f,
                            )
                        }
                        T(formatDuration((video.durationMs / 1000).toInt()), TgTheme.type.caption1, Color.White.copy(0.7f))
                    }
                }
            }
        }
    }
}
