package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import com.abtin.tglass.core.media.MediaPrep
import com.abtin.tglass.core.media.PickedMedia
import kotlinx.coroutines.launch
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

/** Attachment panel: device gallery (or demo tiles) with multi-select, camera, caption, and the attachment type bar. */
@Composable
fun AttachSheet(visible: Boolean, onDismiss: () -> Unit, onSend: (List<MessageContent>) -> Unit) {
    val c = TgTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = com.abtin.tglass.features.main.LocalRepository.current
    val gallery = rememberGalleryState()
    val selectedDemo = remember { mutableStateListOf<Int>() }
    val selected = remember { mutableStateListOf<GalleryItem>() }
    val captured = remember { mutableStateListOf<GalleryItem>() }
    var caption by remember { mutableStateOf("") }
    var preparing by remember { mutableStateOf(false) }
    val selectionCount = selected.size + selectedDemo.size
    var pollOpen by remember { mutableStateOf(false) }
    var contactOpen by remember { mutableStateOf(false) }
    // Telegram allows polls in private chats only with bots and in Saved Messages.
    val currentChatId = (com.abtin.tglass.core.navigation.LocalNavigator.current.top as? com.abtin.tglass.core.navigation.Route.Chat)?.chatId
    val canPoll = !repo.isLive || currentChatId?.let { repo.chat(it)?.type } != com.abtin.tglass.data.ChatType.Private

    fun sendPicked(items: List<PickedMedia>, text: String?) {
        if (items.isEmpty() || preparing) return
        preparing = true
        scope.launch {
            val contents = MediaPrep.prepare(context, items, text)
            preparing = false
            if (contents.isEmpty()) return@launch
            selected.clear(); caption = ""
            onSend(contents)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        gallery.access = hasGalleryAccess(context)
        gallery.reload()
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val items = uris.map { u -> PickedMedia(u, context.contentResolver.getType(u)?.startsWith("video") == true) }
        sendPicked(items, null)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) {
            val item = GalleryItem(u, false, 0)
            captured.add(0, item)
            selected.add(item)
        }
    }
    fun openCamera() {
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val f = java.io.File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        cameraUri = u
        runCatching { cameraLauncher.launch(u) }
    }
    fun openPicker() = runCatching {
        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    fun sendDocuments(uris: List<Uri>) {
        if (uris.isEmpty() || preparing) return
        preparing = true
        scope.launch {
            val files = com.abtin.tglass.core.media.Files.prepareDocuments(context, uris)
            preparing = false
            if (files.isNotEmpty()) onSend(files)
        }
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { sendDocuments(it) }
    val toast = com.abtin.tglass.ui.components.LocalToast.current
    fun sendLocation() {
        if (preparing) return
        preparing = true
        scope.launch {
            val loc = com.abtin.tglass.core.media.Files.currentLocation(context)
            preparing = false
            if (loc == null) toast.show("Location is not available. Turn on location and try again.")
            else onSend(listOf(MessageContent.Location("Location", "%.6f, %.6f".format(java.util.Locale.US, loc.latitude, loc.longitude))))
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) sendLocation() else toast.show("Allow location access to share your location")
    }
    fun requestLocation() {
        val ok = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION).any {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (ok) sendLocation()
        else locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(visible, gallery.access, gallery.version) {
        if (visible && gallery.access) gallery.items = loadRecentMedia(context)
    }
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
                modifier = Modifier.padding(6.dp).navigationBarsPadding().imePadding().fillMaxWidth().height(520.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(IosIcons.Close, onDismiss, size = 40.dp, iconSize = 18.dp)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            T(if (selectionCount == 0) "Recents" else "$selectionCount Selected", TgTheme.type.headline, c.text)
                        }
                        GlassIconButton(TgIcons.AttGallery, { openPicker() }, size = 40.dp, iconSize = 22.dp)
                    }
                    LazyVerticalGrid(
                        GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item(key = "camera") {
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedRectangle(6.dp)).background(Color(0xFF111111)).bounceClickable { openCamera() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(TgIcons.AttCamera, Color.White, 34.dp) }
                        }
                        if (!gallery.access) {
                            item(key = "access", span = { GridItemSpan(2) }) {
                                Column(
                                    Modifier.aspectRatio(2f).clip(RoundedRectangle(6.dp)).background(c.searchField)
                                        .bounceClickable { permissionLauncher.launch(galleryPermissions()) }.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    T("Allow Access", TgTheme.type.headline, c.accent)
                                    Spacer(Modifier.height(2.dp))
                                    T("to show your recent photos and videos here", TgTheme.type.caption1, c.secondaryText, align = TextAlign.Center)
                                }
                            }
                        }
                        val real = captured + gallery.items.filter { g -> captured.none { it.uri == g.uri } }
                        items(real, key = { it.uri.toString() }) { item ->
                            val index = selected.indexOf(item)
                            GalleryTile(item, index, onToggle = { if (index >= 0) selected.remove(item) else if (selected.size < 10) selected.add(item) })
                        }
                        if (!gallery.access && !repo.isLive) {
                            items(GalleryEmojis.indices.toList()) { i ->
                                val (a, b) = avatarColors(i.toLong())
                                val index = selectedDemo.indexOf(i)
                                Box(
                                    Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedRectangle(6.dp))
                                        .background(Brush.linearGradient(listOf(a, b)))
                                        .clickable(remember { MutableInteractionSource() }, null) {
                                            if (index >= 0) selectedDemo.remove(i) else selectedDemo.add(i)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    T(GalleryEmojis[i], TgTheme.type.body.copy(fontSize = 40.sp, lineHeight = 46.sp))
                                    SelectionCircle(index, Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }
                    }
                    if (selectionCount > 0) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(44.dp).clip(Capsule()).background(c.searchField).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                if (caption.isEmpty()) T("Add a caption…", TgTheme.type.body, c.secondaryText)
                                BasicTextField(
                                    caption, { caption = it }, Modifier.fillMaxWidth(),
                                    textStyle = TgTheme.type.body.copy(color = c.text), singleLine = true, cursorBrush = SolidColor(c.accent),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.height(44.dp).clip(Capsule()).background(c.accent).bounceClickable {
                                    if (selected.isNotEmpty()) {
                                        sendPicked(selected.map { it.picked() }, caption)
                                    } else {
                                        val items = selectedDemo.map { i -> MessageContent.Photo(i, listOf(1.33f, 0.75f, 1f, 1.5f)[i % 4], caption.ifBlank { null }, GalleryEmojis[i]) }
                                        selectedDemo.clear(); caption = ""
                                        onSend(items)
                                    }
                                }.padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (preparing) com.abtin.tglass.ui.components.ActivityIndicator(20.dp, Color.White)
                                else T("Send $selectionCount", TgTheme.type.headline, Color.White)
                            }
                        }
                    } else {
                        // iOS 26: attachment types live in a glass capsule, the current one tinted with the accent.
                        GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth().height(64.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                AttachType(TgIcons.AttGallery, "Gallery", selected = true) { openPicker() }
                                AttachType(TgIcons.AttFile, "File") { runCatching { documentLauncher.launch(arrayOf("*/*")) } }
                                AttachType(TgIcons.AttLocation, "Location") { requestLocation() }
                                if (canPoll) AttachType(TgIcons.AttPoll, "Poll") { onDismiss(); pollOpen = true }
                                AttachType(TgIcons.AttContact, "Contact") { onDismiss(); contactOpen = true }
                                if (!repo.isLive) {
                                    // Sample content for the demo; real gifts need their own editor.
                                    AttachType(TgIcons.AttGift, "Gift") { onSend(listOf(MessageContent.Sticker("🎁"))) }
                                }
                                AttachType(TgIcons.AttAudio, "Music") { runCatching { documentLauncher.launch(arrayOf("audio/*")) } }
                            }
                        }
                    }
                }
            }
        }
    }
    NewPollSheet(
        visible = pollOpen,
        onDismiss = { pollOpen = false },
        onSend = { poll ->
            pollOpen = false
            onSend(listOf(poll))
        },
    )
    ContactPickerSheet(
        visible = contactOpen,
        onDismiss = { contactOpen = false },
        onPick = { u ->
            contactOpen = false
            onSend(listOf(MessageContent.Contact(u.name, u.phone, u.id)))
        },
    )
}

@Composable
private fun SelectionCircle(index: Int, modifier: Modifier) {
    val c = TgTheme.colors
    Box(
        modifier
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

@Composable
private fun GalleryTile(item: GalleryItem, index: Int, onToggle: () -> Unit) {
    val px = with(LocalDensity.current) { 130.dp.roundToPx() }
    val thumb = rememberGalleryThumb(item, px)
    val scale by animateFloatAsState(if (index >= 0) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tileScale")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedRectangle(6.dp))
            .background(TgTheme.colors.searchField)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onToggle),
    ) {
        if (thumb != null) {
            Image(thumb, null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }, contentScale = ContentScale.Crop)
        }
        if (item.video) {
            Box(Modifier.align(Alignment.BottomStart).padding(5.dp).clip(Capsule()).background(Color.Black.copy(0.45f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                T(com.abtin.tglass.ui.components.formatDuration((item.durationMs / 1000).toInt()), TgTheme.type.caption2, Color.White, weight = FontWeight.SemiBold)
            }
        }
        SelectionCircle(index, Modifier.align(Alignment.TopEnd))
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
