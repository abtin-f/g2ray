package com.abtin.tglass.features.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.media.RecordedVideoNote
import com.abtin.tglass.core.media.RecordedVoice
import com.abtin.tglass.core.media.VideoNoteRecorder
import com.abtin.tglass.core.media.VoicePlayer
import com.abtin.tglass.core.media.VoiceRecorder
import com.abtin.tglass.data.Message
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Height of the composer's controls (Telegram-iOS 26: the + circle, the Message field and the mic circle). */
internal val ComposerHeight = 42.dp

/**
 * The floating chat composer of Telegram-iOS 26: a glass "+" circle, the glass Message field (grows up to 8 lines,
 * then scrolls; emoji button inside) and a glass mic circle that turns into the blue send button.
 * Holding the mic records a voice message (or a round video, after a tap switched it to the camera):
 * slide left to cancel, slide up to lock; a locked voice message can be stopped and listened to before sending.
 */
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    replyTo: Message?,
    replyName: String?,
    editing: Message?,
    onCancelContext: () -> Unit,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    /** Seconds, recorded file (null if the microphone was unavailable) and waveform. */
    onVoice: (seconds: Int, path: String?, waveform: List<Float>?) -> Unit,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Long-press on the send button (e.g. "Send Without Sound"); null disables it. */
    onSendLongPress: (() -> Unit)? = null,
    /** Camera for round video messages; when set, a tap on the mic switches it to the camera (Telegram). */
    videoRecorder: VideoNoteRecorder? = null,
    onVideoNote: (RecordedVideoNote) -> Unit = {},
) {
    val c = TgTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    TrackKeyboardHeight()

    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {}
    fun granted(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun hasMic() = granted(android.Manifest.permission.RECORD_AUDIO)
    fun hasCamera() = hasMic() && granted(android.Manifest.permission.CAMERA)

    var recording by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var recordingVideo by remember { mutableStateOf(false) }
    // Mic / camera toggle (a short tap on the button switches it).
    var videoMode by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    // A locked voice message that was stopped: listened to, then sent or deleted.
    var preview by remember { mutableStateOf<RecordedVoice?>(null) }
    var videoSendRequested by remember { mutableStateOf(false) }
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val currentOnVoice by rememberUpdatedState(onVoice)
    val currentOnVideoNote by rememberUpdatedState(onVideoNote)

    fun springBack() {
        scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
        scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
    }

    /** Ends the current recording: [send] sends it, otherwise it is thrown away. */
    fun finishRecording(send: Boolean) {
        if (!recording) return
        val wasVideo = recordingVideo
        recording = false
        locked = false
        recordingVideo = false
        level = 0f
        springBack()
        if (wasVideo) {
            videoSendRequested = send
            videoRecorder?.stop(send)
            return
        }
        val voice = recorder.stop(discard = !send)
        if (send && voice != null) {
            Haptics.confirm(view)
            currentOnVoice(voice.seconds, voice.path, voice.waveform)
        } else if (send) {
            // Shorter than a second (or the microphone failed): nothing to send.
            Haptics.reject(view)
        }
    }

    /** Stop button of a locked voice recording: keep it for listening before sending. */
    fun stopToPreview() {
        if (!recording || recordingVideo) return
        val voice = recorder.stop(discard = false)
        recording = false
        locked = false
        level = 0f
        springBack()
        if (voice != null) preview = voice else Haptics.reject(view)
    }

    fun previewKey(p: RecordedVoice) = "draft:${p.path}"

    fun discardPreview() {
        val p = preview ?: return
        if (VoicePlayer.currentKey == previewKey(p)) VoicePlayer.stop()
        runCatching { File(p.path).delete() }
        preview = null
    }

    fun sendPreview() {
        val p = preview ?: return
        if (VoicePlayer.currentKey == previewKey(p)) VoicePlayer.stop()
        preview = null
        Haptics.confirm(view)
        currentOnVoice(p.seconds, p.path, p.waveform)
    }

    /** Opens the camera for a round video; false if the previous one is still being finished. */
    fun startVideo(): Boolean {
        val vr = videoRecorder ?: return false
        if (vr.active) return false
        videoSendRequested = false
        vr.open { note ->
            if (note != null) {
                Haptics.confirm(view)
                currentOnVideoNote(note)
            } else if (videoSendRequested) {
                Haptics.reject(view)
            }
        }
        return true
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stop(discard = true)
            if (recordingVideo) videoRecorder?.cancel()
            preview?.let { p ->
                if (VoicePlayer.currentKey == previewKey(p)) VoicePlayer.stop()
                runCatching { File(p.path).delete() }
            }
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            if (recordingVideo) {
                val vr = videoRecorder
                if (vr == null || !vr.active) {
                    // The camera failed or the recording ended by itself (screen off, time limit).
                    recording = false
                    locked = false
                    recordingVideo = false
                    springBack()
                    break
                }
                elapsed = vr.elapsedMs()
                level = 0f // CameraX gives no live level here; the ring just breathes
                if (elapsed >= VideoNoteRecorder.MaxMs) {
                    finishRecording(true)
                    break
                }
            } else {
                elapsed = System.currentTimeMillis() - startedAt
                level = recorder.sample()
            }
            delay(50)
        }
    }

    val cancelDistance = 120.dp
    val lockDistance = 90.dp

    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        // Reply / edit context
        AnimatedVisibility(
            visible = (replyTo != null || editing != null) && !recording && preview == null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val target = editing ?: replyTo
            GlassBox(onClick = null, shape = RoundedRectangle(20.dp), modifier = Modifier.padding(start = 50.dp, end = 50.dp, bottom = 6.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (editing != null) TgIcons.CtxEdit else TgIcons.CtxReply, c.accent, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(2.dp).height(32.dp).clip(Capsule()).background(c.accent))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        T(if (editing != null) "Edit Message" else "Reply to ${replyName ?: ""}", TgTheme.type.footnote.copy(fontSize = 14.sp), c.accent, weight = FontWeight.SemiBold, maxLines = 1)
                        T(target?.preview ?: "", TgTheme.type.footnote.copy(fontSize = 14.sp, textDirection = TextDirection.Content), c.text, maxLines = 1)
                    }
                    Box(Modifier.size(32.dp).fadeClickable(onClick = onCancelContext), contentAlignment = Alignment.Center) {
                        Icon(IosIcons.Close, c.secondaryText, 18.dp)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            // "+" (attachments); hidden while recording, a red trash can while a recorded voice is waiting to be sent.
            AnimatedVisibility(
                !recording && preview == null,
                enter = fadeIn() + scaleIn(initialScale = 0.5f) + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut() + scaleOut(targetScale = 0.5f) + shrinkHorizontally(shrinkTowards = Alignment.Start),
            ) {
                Row {
                    GlassIconButton(ComposerIcons.Plus, onAttach, size = ComposerHeight, iconSize = 24.dp, tint = c.text)
                    Spacer(Modifier.width(8.dp))
                }
            }
            AnimatedVisibility(
                preview != null,
                enter = fadeIn() + scaleIn(initialScale = 0.5f) + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut() + scaleOut(targetScale = 0.5f) + shrinkHorizontally(shrinkTowards = Alignment.Start),
            ) {
                Row {
                    GlassIconButton(ComposerIcons.Trash, { discardPreview() }, size = ComposerHeight, iconSize = 22.dp, tint = c.destructive)
                    Spacer(Modifier.width(8.dp))
                }
            }

            // The Message field, the recording status or the recorded voice.
            GlassBox(
                onClick = null,
                shape = RoundedRectangle(ComposerHeight / 2),
                modifier = Modifier.weight(1f).heightIn(min = ComposerHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                val p = preview
                when {
                    recording -> RecordingStatus(elapsed, locked, dragX.value, cancelDistance, onCancel = { Haptics.tap(view); finishRecording(false) })
                    p != null -> VoiceDraft(p, previewKey(p))
                    else -> Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 2.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) T("Message", TgTheme.type.body, c.secondaryText, maxLines = 1)
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                // Content direction: Persian / Arabic lines go right-to-left.
                                textStyle = TgTheme.type.body.copy(color = c.text, textDirection = TextDirection.Content),
                                cursorBrush = SolidColor(c.accent),
                                minLines = 1,
                                maxLines = 8,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) },
                            )
                        }
                        Box(Modifier.height(ComposerHeight).width(40.dp).fadeClickable(onClick = onTogglePanel), contentAlignment = Alignment.Center) {
                            Crossfade(panelOpen, label = "panelIcon") { open ->
                                Icon(if (open) IosIcons.Keyboard else ComposerIcons.Smiley, c.secondaryText, if (open) 24.dp else 25.dp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            // Mic / send
            val sendMode = text.isNotBlank() || editing != null || preview != null
            Box(Modifier.size(ComposerHeight), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    !sendMode && !(recording && locked),
                    enter = fadeIn() + scaleIn(initialScale = 0.4f),
                    exit = fadeOut() + scaleOut(targetScale = 0.4f),
                ) {
                    val micModifier = Modifier.pointerInput(videoRecorder) {
                        val cancelAt = cancelDistance.toPx()
                        val lockAt = lockDistance.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            if (videoRecorder != null) {
                                // Telegram: a short tap switches between voice and video messages, holding records.
                                val released = withTimeoutOrNull(HoldToRecordMs) {
                                    var up = false
                                    while (!up) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id }
                                        if (ch == null || !ch.pressed) up = true else ch.consume()
                                    }
                                    up
                                } ?: false
                                if (released) {
                                    videoMode = !videoMode
                                    Haptics.tap(view)
                                    return@awaitEachGesture
                                }
                            }
                            val video = videoRecorder != null && videoMode
                            if (video && !hasCamera()) {
                                cameraPermission.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
                                drainUntilUp()
                                return@awaitEachGesture
                            }
                            if (!video && !hasMic()) {
                                micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                drainUntilUp()
                                return@awaitEachGesture
                            }
                            // Only one sound at a time.
                            VoicePlayer.stop()
                            VideoNotePlayback.activeKey = null
                            val started = if (video) startVideo() else recorder.start()
                            if (!started) {
                                Haptics.reject(view)
                                drainUntilUp()
                                return@awaitEachGesture
                            }
                            recordingVideo = video
                            startedAt = System.currentTimeMillis()
                            elapsed = 0
                            level = 0f
                            recording = true
                            Haptics.longPress(view)
                            var outcome = 0 // 0 = send, 1 = cancel, 2 = lock
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                if (!recording) break
                                val rawX = (ch.position.x - down.position.x).coerceAtMost(0f)
                                val rawY = (ch.position.y - down.position.y).coerceAtMost(0f)
                                // Follow the finger along the dominant direction (left = cancel, up = lock), like iOS.
                                val horizontal = -rawX >= -rawY
                                val dx = if (horizontal) rawX else 0f
                                val dy = if (horizontal) 0f else rawY
                                scope.launch { dragX.snapTo(dx); dragY.snapTo(dy) }
                                if (-dx > cancelAt) { outcome = 1; break }
                                if (-dy > lockAt) { outcome = 2; break }
                            }
                            when (outcome) {
                                1 -> {
                                    Haptics.tap(view)
                                    finishRecording(false)
                                }
                                2 -> if (recording) {
                                    locked = true
                                    Haptics.longPress(view)
                                    springBack()
                                }
                                else -> finishRecording(true)
                            }
                        }
                    }
                    Box(Modifier.size(ComposerHeight).then(micModifier), contentAlignment = Alignment.Center) {
                        if (!recording) {
                            GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.size(ComposerHeight)) {
                                Crossfade(videoMode, label = "micMode") { v ->
                                    Box(Modifier.size(ComposerHeight), contentAlignment = Alignment.Center) {
                                        Icon(if (v) IosIcons.Video else IosIcons.Mic, c.text, if (v) 25.dp else 23.dp)
                                    }
                                }
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    sendMode && !recording,
                    enter = fadeIn() + scaleIn(initialScale = 0.4f),
                    exit = fadeOut() + scaleOut(targetScale = 0.4f),
                ) {
                    Box(
                        Modifier.size(ComposerHeight).clip(CircleShape).background(c.accent).bounceLongClickable(
                            onLongClick = if (preview != null) null else onSendLongPress,
                        ) {
                            if (preview != null) sendPreview() else onSend()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (editing != null && preview == null) IosIcons.Checkmark else IosIcons.ArrowUp, Color.White, 22.dp)
                    }
                }
                if (recording) {
                    RecordingControls(
                        level = level,
                        dragX = dragX.value,
                        dragY = dragY.value,
                        locked = locked,
                        video = recordingVideo,
                        lockDistance = lockDistance,
                        onSend = { finishRecording(true) },
                        onStop = { stopToPreview() },
                    )
                }
            }
        }
    }
}

/** Waits until every finger is lifted, swallowing the events. */
private suspend fun AwaitPointerEventScope.drainUntilUp() {
    do {
        val ev = awaitPointerEvent()
        ev.changes.forEach { it.consume() }
    } while (ev.changes.any { it.pressed })
}

/** How long the mic / camera button must be held before recording starts (a shorter tap switches the mode). */
private const val HoldToRecordMs = 250L
