package com.abtin.tglass.features.calls

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.CallRecord
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.EmptyState
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.SegmentedControl
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.BackButton
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatDuration
import com.abtin.tglass.ui.components.formatListDate
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/**
 * Spec §30: recent calls. Real accounts show their call history (tap opens the chat, Edit deletes entries);
 * placing calls is handed off to the official app (see [requestCall]).
 */
@Composable
fun CallsScreen(backdrop: LayerBackdrop, isTab: Boolean) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val toast = LocalToast.current
    val context = LocalContext.current
    val c = TgTheme.colors
    var filter by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val calls = repo.calls.filter { filter == 0 || it.missed }
    LaunchedEffect(calls.isEmpty()) { if (calls.isEmpty()) editing = false }

    fun confirmDelete(records: List<CallRecord>, title: String) {
        if (records.isEmpty()) return
        sheet.show(SheetRequest(actions = listOf(SheetAction(title, destructive = true) { repo.deleteCallRecords(records) })))
    }

    fun newCall() {
        if (repo.isLive) {
            requestNewCall(context, sheet, toast)
        } else {
            val people = repo.contacts.take(6)
            sheet.show(SheetRequest(title = "New Call", actions = people.map { u ->
                SheetAction(u.name) { requestCall(context, repo, nav, sheet, toast, u.id, video = false) }
            }))
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().layerBackdrop(backdrop),
            contentPadding = PaddingValues(top = top + 62.dp, bottom = bottom + if (isTab) 110.dp else 20.dp),
        ) {
            if (calls.isEmpty()) item {
                if (filter == 1) EmptyState("📞", "No Missed Calls", "Your missed calls will appear here.")
                else EmptyState("📞", "No Recent Calls", "Your recent calls will appear here.")
            }
            items(calls, key = { "${it.chatId}:${it.id}" }) { call ->
                val u = repo.user(call.userId) ?: return@items
                val deleteOne = { confirmDelete(listOf(call), "Delete from Call History") }
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .iosClickable(onLongClick = deleteOne) {
                                if (editing) deleteOne() else nav.push(Route.Chat(repo.privateChatWith(u.id)))
                            }
                            .height(62.dp)
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editing) {
                            Box(Modifier.size(34.dp).fadeClickable(onClick = deleteOne), contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(c.destructive), contentAlignment = Alignment.Center) {
                                    Box(Modifier.width(11.dp).height(2.dp).background(Color.White))
                                }
                            }
                        }
                        Avatar(u.name, u.id, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(u.name, TgTheme.type.headline, if (call.missed) c.destructive else c.text, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (call.video) TgIcons.ClOutgoingVideo else TgIcons.ClOutgoing, c.secondaryText, 16.dp, Modifier.graphicsLayer { if (!call.outgoing) rotationZ = 180f })
                                Spacer(Modifier.width(4.dp))
                                val kind = if (call.video) "Video" else if (call.outgoing) "Outgoing" else "Incoming"
                                T(if (call.durationSec > 0) "$kind (${formatDuration(call.durationSec)})" else kind, TgTheme.type.subheadline, c.secondaryText, maxLines = 1)
                            }
                        }
                        T(formatListDate(call.date), TgTheme.type.subheadline, c.secondaryText)
                        if (!editing) {
                            Box(Modifier.size(40.dp).fadeClickable { nav.push(Route.UserProfile(u.id)) }, contentAlignment = Alignment.Center) {
                                Icon(TgIcons.ClInfo, c.accent, 26.dp)
                            }
                        } else Spacer(Modifier.width(8.dp))
                    }
                    Separator(Modifier.align(Alignment.BottomStart), startPadding = if (editing) 102.dp else 68.dp)
                }
            }
        }
        GlassTopBar(
            title = null,
            left = {
                if (isTab) GlassTextButton(if (editing) "Done" else "Edit", { if (editing || calls.isNotEmpty()) editing = !editing }, bold = editing)
                else BackButton({ nav.pop() })
            },
            center = { SegmentedControl(listOf("All", "Missed"), filter, { filter = it }, Modifier.width(180.dp)) },
            right = {
                if (editing) {
                    GlassTextButton("Clear", {
                        confirmDelete(calls, if (filter == 1) "Clear Missed Calls" else "Clear Call History")
                    }, color = c.destructive)
                    if (!isTab) {
                        Spacer(Modifier.width(8.dp))
                        GlassTextButton("Done", { editing = false }, bold = true)
                    }
                } else {
                    if (!isTab && calls.isNotEmpty()) {
                        GlassTextButton("Edit", { editing = true })
                        Spacer(Modifier.width(8.dp))
                    }
                    GlassIconButton(TgIcons.ClNewCall, { newCall() })
                }
            },
        )
    }
}

/** Calls pushed from Settings → Recent Calls. */
@Composable
fun CallsPage() {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) { CallsScreen(backdrop, isTab = false) }
}

/** Spec §30 active call. */
@Composable
fun ActiveCallScreen(userId: Long, video: Boolean) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val u = repo.user(userId) ?: return
    var status by remember { mutableStateOf("Requesting…") }
    var seconds by remember { mutableIntStateOf(-1) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(video) }
    LaunchedEffect(Unit) {
        delay(1200); status = "Ringing…"
        delay(2200); seconds = 0
        while (true) { delay(1000); seconds++ }
    }
    val t = rememberInfiniteTransition(label = "callBg")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "shift")
    val pulse by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF20A4D7), Color(0xFF3F8BEA), Color(0xFF8148EC)),
                start = androidx.compose.ui.geometry.Offset(0f, 1000f * shift),
                end = androidx.compose.ui.geometry.Offset(1200f, 2400f - 800f * shift),
            )
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { val s = if (seconds < 0) pulse else 1f; scaleX = s; scaleY = s }) {
                Avatar(u.name, u.id, 150.dp)
            }
            Spacer(Modifier.height(24.dp))
            T(u.name, TgTheme.type.title1, Color.White)
            Spacer(Modifier.height(6.dp))
            T(if (seconds >= 0) formatDuration(seconds) else status, TgTheme.type.body, Color.White.copy(0.8f))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CallButton(IosIcons.Speaker, "speaker", speaker) { speaker = !speaker }
            CallButton(IosIcons.Video, "video", camera) { camera = !camera }
            CallButton(if (muted) IosIcons.MicSlash else IosIcons.Mic, "mute", muted) { muted = !muted }
            CallButton(IosIcons.PhoneDown, "end", false, end = true) { nav.pop() }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, active: Boolean, end: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(if (end) Color(0xFFFF3B30) else if (active) Color.White else Color.White.copy(alpha = 0.22f))
                .bounceClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, if (active && !end) Color(0xFF3F8BEA) else Color.White, 30.dp)
        }
        Spacer(Modifier.height(6.dp))
        T(label, TgTheme.type.footnote.copy(fontSize = 13.sp), Color.White, weight = FontWeight.Medium)
    }
}
