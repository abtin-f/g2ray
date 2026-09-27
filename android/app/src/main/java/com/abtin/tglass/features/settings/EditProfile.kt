package com.abtin.tglass.features.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Edit Profile as in Telegram for iPhone: Cancel / Done in the bar (Cancel asks before throwing edits away),
 * the photo on top (take / choose / delete, then "Move and Scale" in a circle), name, bio and username.
 * A new photo is uploaded right away, like on iOS; the text fields are saved with Done.
 */
@Composable
fun EditProfileScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = TgTheme.colors
    val me = repo.me
    val settings = com.abtin.tglass.core.design.LocalAppSettings.current
    LaunchedEffect(me.id) {
        repo.loadChatInfo(me.id)
        repo.loadProfileExtras()
    }
    val serverBio = repo.chatInfo(me.id)?.about ?: me.bio ?: ""

    var first by rememberSaveable(me.id) { mutableStateOf(me.firstName) }
    var last by rememberSaveable(me.id) { mutableStateOf(me.lastName) }
    // The bio arrives with the full user info; fill it in once unless the user already typed.
    var bio by rememberSaveable(me.id) { mutableStateOf(serverBio) }
    var bioTouched by rememberSaveable(me.id) { mutableStateOf(false) }
    LaunchedEffect(serverBio) { if (!bioTouched) bio = serverBio }

    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var localPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }

    // Birthday: edited inline and saved with Done, like on iOS.
    var birthday by remember { mutableStateOf(repo.myBirthdate) }
    var birthdayTouched by rememberSaveable { mutableStateOf(false) }
    var birthdayEditing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(repo.myBirthdate) { if (!birthdayTouched) birthday = repo.myBirthdate }
    val birthdayChanged = birthdayTouched && birthday != repo.myBirthdate

    val changed = first.trim() != me.firstName || last.trim() != me.lastName || bio.trim() != serverBio.trim() || birthdayChanged
    val hasPhoto = !photoRemoved && (localPhoto != null || repo.avatar(me.id) != null)

    fun close() = nav.pop()
    fun cancel() {
        if (!changed) { close(); return }
        sheet.show(SheetRequest(
            message = "Discard your changes?",
            actions = listOf(SheetAction("Discard Changes", destructive = true) { close() }),
        ))
    }
    fun done() {
        if (saving) return
        if (!changed) { close(); return }
        if (first.isBlank()) { toast.show("Please enter your first name"); return }
        saving = true
        val profileChanged = first.trim() != me.firstName || last.trim() != me.lastName || bio.trim() != serverBio.trim()
        fun saveBirthday() {
            if (!birthdayChanged) { saving = false; close(); return }
            repo.setBirthdate(birthday) { err ->
                saving = false
                if (err != null) sheet.show(SheetRequest(title = "Birthday", message = err, alert = true, actions = emptyList(), cancel = "OK"))
                else close()
            }
        }
        if (profileChanged) {
            repo.updateProfile(first.trim(), last.trim(), bio.trim()) { err ->
                if (err != null) {
                    saving = false
                    sheet.show(SheetRequest(title = "Couldn't save", message = err, alert = true, actions = emptyList(), cancel = "OK"))
                } else saveBirthday()
            }
        } else saveBirthday()
    }
    BackHandler(enabled = changed || cropSource != null) { if (cropSource != null) cropSource = null else cancel() }

    fun upload(path: String) {
        localPhoto = path
        photoRemoved = false
        uploading = true
        repo.updateProfilePhoto(path) { err ->
            uploading = false
            if (err != null) {
                localPhoto = null
                sheet.show(SheetRequest(title = "Couldn't set photo", message = err, alert = true, actions = emptyList(), cancel = "OK"))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cropSource = uri }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { cropSource = it } }
    fun takePhoto() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "AVATAR_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        cameraUri = u
        runCatching { camera.launch(u) }.onFailure { toast.show("No camera app") }
    }
    fun choosePhoto() {
        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { toast.show("Can't open the photo picker") }
    }
    fun photoMenu() {
        sheet.show(SheetRequest(actions = listOfNotNull(
            SheetAction("Take Photo") { takePhoto() },
            SheetAction("Choose from Library") { choosePhoto() },
            if (hasPhoto) SheetAction("Delete Photo", destructive = true) {
                uploading = true
                repo.deleteProfilePhoto { err ->
                    uploading = false
                    if (err != null) toast.show(err) else { localPhoto = null; photoRemoved = true }
                }
            } else null,
        )))
    }

    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 64.dp, bottom = bottom + 30.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(100.dp).clip(CircleShape).fadeClickable { photoMenu() }, contentAlignment = Alignment.Center) {
                            if (photoRemoved) Avatar(me.name, 3, 100.dp, photoPeer = Long.MIN_VALUE)
                            else Avatar(me.name, 3, 100.dp, photoPeer = me.id)
                            localPhoto?.let { TgImage(ImageRef(0, it, null, 800, 800), Modifier.matchParentSize().clip(CircleShape)) }
                            if (uploading) {
                                Box(Modifier.matchParentSize().background(Color.Black.copy(0.35f)))
                                ActivityIndicator(28.dp, Color.White)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        T(
                            if (hasPhoto) "Set New Photo" else "Set Photo", TgTheme.type.body, c.accent,
                            modifier = Modifier.fadeClickable { photoMenu() },
                        )
                    }
                }
                item {
                    Section(footer = "Enter your name and add an optional profile photo.") {
                        ProfileField(first, { first = it.take(64) }, "First Name")
                        ProfileField(last, { last = it.take(64) }, "Last Name", divider = false)
                    }
                    Spacer(Modifier.height(24.dp))
                    Section(header = "Bio", footer = "You can add a few lines about yourself. Choose who can see your bio in Settings.") {
                        ProfileField(bio, { bio = it.take(70); bioTouched = true }, "Bio", divider = false, trailingHint = "${70 - bio.length}")
                    }
                    Spacer(Modifier.height(24.dp))
                    Section(footer = "Choose who can see your birthday in Settings → Privacy and Security.") {
                        Cell(
                            "Birthday", icon = SettingsGlyphs.Gift, iconColor = Color(0xFFFF2D55),
                            value = birthday?.label() ?: "Add", chevron = false,
                            divider = birthdayEditing,
                            onClick = {
                                if (birthday == null) { birthday = com.abtin.tglass.data.ProfileBirthdate(1, 1, 2000); birthdayTouched = true }
                                birthdayEditing = !birthdayEditing
                            },
                        )
                        val b = birthday
                        if (birthdayEditing && b != null) {
                            BirthdayPicker(b) { birthday = it; birthdayTouched = true }
                            Separator(startPadding = 16.dp)
                            Cell("Remove Birthday", titleColor = c.destructive, chevron = false, divider = false, onClick = {
                                birthday = null; birthdayTouched = true; birthdayEditing = false
                            })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Section {
                        Cell("Phone Number", icon = SettingsGlyphs.Phone, iconColor = Color(0xFF34C759), value = me.phone, onClick = {
                            sheet.show(SheetRequest(
                                title = "Change Number",
                                message = "Your number is ${me.phone}. Changing it moves your account, chats and media to the new number; TGlass can't do this yet, so use the official Telegram app (Settings → Edit → Phone Number).",
                                alert = true, actions = emptyList(), cancel = "OK",
                            ))
                        })
                        Cell("Username", icon = SettingsGlyphs.At, iconColor = Color(0xFF32ADE6), value = me.username?.let { "@$it" } ?: "", onClick = {
                            nav.push(com.abtin.tglass.core.navigation.Route.SettingsPage(Page.Username))
                        })
                        Cell("Your Color", icon = SettingsGlyphs.Palette, iconColor = Color(0xFFFF9500), trailing = {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(com.abtin.tglass.data.NameColors.color(repo.myNameColorId, c.isDark)))
                            Spacer(Modifier.width(8.dp))
                            com.abtin.tglass.ui.components.Icon(com.abtin.tglass.ui.components.IosIcons.ChevronRight, c.tertiaryText, 14.dp)
                        }, onClick = { nav.push(com.abtin.tglass.core.navigation.Route.SettingsPage(Page.NameColor)) })
                        Cell("Personal Channel", icon = SettingsGlyphs.Channel, iconColor = Color(0xFF007AFF), value = repo.myPersonalChannel?.title ?: "Add", divider = false, onClick = {
                            nav.push(com.abtin.tglass.core.navigation.Route.SettingsPage(Page.PersonalChannel))
                        })
                    }
                    Spacer(Modifier.height(24.dp))
                    Section {
                        Box(Modifier.fillMaxWidth().height(50.dp).fadeClickable {
                            sheet.show(SheetRequest(title = "Log out?", message = "You will return to the welcome screen.", alert = true, actions = listOf(SheetAction("Log Out", destructive = true) {
                                repo.logOut()
                                PasscodeLock.disable()
                                settings.updateLoggedIn(false)
                                settings.updateDemoMode(false)
                                nav.resetTo(com.abtin.tglass.core.navigation.Route.Welcome)
                            })))
                        }, contentAlignment = Alignment.Center) {
                            T("Log Out", TgTheme.type.body, c.destructive)
                        }
                    }
                }
            }
            GlassTopBar(
                title = null,
                fade = c.groupedBackground,
                left = { GlassTextButton("Cancel", { cancel() }) },
                right = {
                    if (saving) {
                        GlassBox(onClick = null, modifier = Modifier.height(44.dp), shape = Capsule()) {
                            Box(Modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) { ActivityIndicator(20.dp) }
                        }
                    } else {
                        GlassTextButton("Done", { done() }, color = if (changed) c.accent else c.text, bold = true)
                    }
                },
            )
            AnimatedVisibility(cropSource != null, enter = fadeIn(), exit = fadeOut()) {
                cropSource?.let { src ->
                    PhotoCropper(
                        source = src,
                        onCancel = { cropSource = null },
                        onDone = { path -> cropSource = null; upload(path) },
                        onError = { cropSource = null; toast.show("Can't read this photo") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileField(value: String, onValue: (String) -> Unit, placeholder: String, divider: Boolean = true, prefix: String? = null, trailingHint: String? = null) {
    val c = TgTheme.colors
    Box {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) T(prefix, TgTheme.type.body, c.secondaryText)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText)
                BasicTextField(
                    value, onValue, Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TgTheme.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                )
            }
            if (trailingHint != null) T(trailingHint, TgTheme.type.footnote, c.tertiaryText)
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}

/**
 * iOS "Move and Scale": the photo under a circular window, pinch to zoom and drag to move; Choose writes the
 * square inside the circle to a JPEG and passes its path to [onDone].
 */
@Composable
private fun PhotoCropper(source: Uri, onCancel: () -> Unit, onDone: (String) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source) {
        val b = withContext(Dispatchers.IO) { runCatching { loadOriented(context, source) }.getOrNull() }
        if (b == null) onError() else bitmap = b
    }
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var ox by remember(source) { mutableFloatStateOf(0f) }
    var oy by remember(source) { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = bitmap
        if (bmp == null) {
            ActivityIndicator(28.dp, Color.White, Modifier.align(Alignment.Center))
        } else {
            val image: ImageBitmap = remember(bmp) { bmp.asImageBitmap() }
            val padPx = with(LocalDensity.current) { 16.dp.toPx() }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val d = min(w, h) - 2 * padPx
                val cx = w / 2f
                val cy = h / 2f
                val base = max(d / bmp.width, d / bmp.height)
                fun clamp() {
                    val s = base * zoom
                    val mx = ((bmp.width * s - d) / 2f).coerceAtLeast(0f)
                    val my = ((bmp.height * s - d) / 2f).coerceAtLeast(0f)
                    ox = ox.coerceIn(-mx, mx)
                    oy = oy.coerceIn(-my, my)
                }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(bmp, w, h) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            val f = newZoom / zoom
                            // Keep the point under the fingers in place while zooming.
                            val rx = centroid.x - cx
                            val ry = centroid.y - cy
                            ox = (ox - rx) * f + rx + pan.x
                            oy = (oy - ry) * f + ry + pan.y
                            zoom = newZoom
                            clamp()
                        }
                    },
                ) {
                    val s = base * zoom
                    val iw = bmp.width * s
                    val ih = bmp.height * s
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset((cx - iw / 2 + ox).roundToInt(), (cy - ih / 2 + oy).roundToInt()),
                        dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
                    )
                    val hole = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(Offset(cx, cy), d / 2f))
                    }
                    drawPath(hole, Color.Black.copy(alpha = 0.6f))
                    drawCircle(Color.White.copy(alpha = 0.5f), d / 2f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
                }

                fun choose() {
                    if (busy) return
                    busy = true
                    val s = base * zoom
                    val left = ((cx - d / 2f) - (cx - bmp.width * s / 2f + ox)) / s
                    val topY = ((cy - d / 2f) - (cy - bmp.height * s / 2f + oy)) / s
                    val side = d / s
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { runCatching { writeCrop(context, bmp, left, topY, side) }.getOrNull() }
                        busy = false
                        if (path == null) onError() else onDone(path)
                    }
                }

                T(
                    "Move and Scale", TgTheme.type.headline, Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp),
                )
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("Cancel", TgTheme.type.body, Color.White, modifier = Modifier.fadeClickable { onCancel() }.padding(8.dp))
                    if (busy) ActivityIndicator(22.dp, Color.White)
                    else T("Choose", TgTheme.type.body, Color.White, weight = FontWeight.SemiBold, modifier = Modifier.fadeClickable { choose() }.padding(8.dp))
                }
            }
        }
    }
}

/** Decodes [uri] at most 2048 px on the long side, rotated upright according to its EXIF data. */
private fun loadOriented(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
    val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val rotation = runCatching {
        resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrNull() ?: 0f
    if (rotation == 0f) return bmp
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
}

/** Cuts the square [left], [top], [side] (bitmap pixels) out of [bmp], scales it to at most 1024 px and saves it as JPEG. */
private fun writeCrop(context: Context, bmp: Bitmap, left: Float, top: Float, side: Float): String {
    val size = side.roundToInt().coerceIn(1, min(bmp.width, bmp.height))
    val x = left.roundToInt().coerceIn(0, bmp.width - size)
    val y = top.roundToInt().coerceIn(0, bmp.height - size)
    var out = Bitmap.createBitmap(bmp, x, y, size, size)
    // Telegram wants at least 160 px; 640–1024 px looks sharp everywhere.
    val target = size.coerceIn(640, 1024)
    if (target != size) out = Bitmap.createScaledBitmap(out, target, target, true)
    val dir = File(context.cacheDir, "upload").apply { mkdirs() }
    val file = File(dir, "avatar_${System.nanoTime()}.jpg")
    FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return file.absolutePath
}

/** Inline iOS-style date wheels (day · month · year, year optional) under the Birthday row. */
@Composable
private fun BirthdayPicker(value: com.abtin.tglass.data.ProfileBirthdate, onChange: (com.abtin.tglass.data.ProfileBirthdate) -> Unit) {
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    // Year list: "—" (no year) then this year back to 1900.
    val years = remember(thisYear) { listOf("—") + (thisYear downTo 1900).map { it.toString() } }
    val yearIndex = if (value.year <= 0) 0 else (thisYear - value.year + 1).coerceIn(1, years.lastIndex)
    val days = com.abtin.tglass.data.ProfileBirthdate.daysIn(value.month, value.year)
    fun emit(day: Int, month: Int, year: Int) {
        val d = day.coerceIn(1, com.abtin.tglass.data.ProfileBirthdate.daysIn(month, year))
        onChange(com.abtin.tglass.data.ProfileBirthdate(d, month, year))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        WheelPicker((1..days).map { it.toString() }, (value.day - 1).coerceIn(0, days - 1), { emit(it + 1, value.month, value.year) }, Modifier.weight(0.8f))
        WheelPicker(com.abtin.tglass.data.ProfileBirthdate.Months, value.month - 1, { emit(value.day, it + 1, value.year) }, Modifier.weight(1.6f))
        WheelPicker(years, yearIndex, { emit(value.day, value.month, if (it == 0) 0 else thisYear - it + 1) }, Modifier.weight(1f))
    }
}

/** A single UIPickerView-like wheel: snaps to the middle row and reports it when scrolling stops. */
@Composable
private fun WheelPicker(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    val rowHeight = 34.dp
    val last = (labels.size - 1).coerceAtLeast(0)
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, last))
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = state)
    fun centerIndex(): Int {
        val info = state.layoutInfo
        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
        return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: selected
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val i = centerIndex().coerceIn(0, last)
            if (i != selected) onSelect(i)
        }
    }
    // Follow outside changes (e.g. the day list got shorter).
    LaunchedEffect(selected, labels.size) {
        if (!state.isScrollInProgress && centerIndex() != selected && selected in labels.indices) state.scrollToItem(selected)
    }
    Box(modifier.height(rowHeight * 5), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(rowHeight).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(c.searchField))
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * 2),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(labels.size) { i ->
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
                    T(labels[i], TgTheme.type.body, if (i == selected) c.text else c.secondaryText, maxLines = 1)
                }
            }
        }
    }
}
