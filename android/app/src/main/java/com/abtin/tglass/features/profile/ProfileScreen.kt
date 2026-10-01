package com.abtin.tglass.features.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatInfo
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.MediaKind
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.ProfileGift
import com.abtin.tglass.data.SharedKind
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.User
import com.abtin.tglass.features.chat.ChatJumpRequest
import com.abtin.tglass.features.chat.deleteAlert
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.features.media.ForwardSheet
import com.abtin.tglass.ui.components.CollapsedTitle
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.formatCount
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.rememberFileImage
import com.abtin.tglass.ui.components.alert
import com.abtin.tglass.ui.components.showError
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Status line under the name: online / last seen, members, subscribers. */
private fun profileSubtitle(chat: Chat, user: User?, info: ChatInfo?, repo: TelegramRepository): Pair<String?, Boolean> {
    val members = chat.members.takeIf { it > 0 } ?: info?.memberCount ?: 0
    return when (chat.type) {
        ChatType.Private -> (user?.status) to (user?.online == true)
        ChatType.Bot -> "bot" to false
        ChatType.Saved -> null to false
        ChatType.Group -> {
            val online = repo.onlineMemberCount(chat.id)
            when {
                members <= 0 -> "group" to false
                online != null && online > 1 -> "${formatCount(members)} members, ${formatCount(online)} online" to false
                else -> "${formatCount(members)} ${if (members == 1) "member" else "members"}" to false
            }
        }
        ChatType.Channel -> (if (members > 0) "${formatCount(members)} ${if (members == 1) "subscriber" else "subscribers"}" else "channel") to false
    }
}

/**
 * Profile of a user, bot, group or channel, like Telegram iOS 26: full-width photo header (or a colored page
 * with a round avatar and floating gifts), glass circle buttons, info card, sliding glass tab switcher with
 * Gifts / Media / Files / Links / Music / Voice / GIFs / Groups. Pull down to expand the avatar; tap it for the
 * photo viewer. Glass controls are drawn above the list, never inside its backdrop layer.
 */
@Composable
fun ProfileScreen(chatId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val toast = LocalToast.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val c = TgTheme.colors
    val chat = repo.chat(chatId) ?: return
    val userId = chat.peerUserId
    val user = userId?.let { repo.user(it) }
    val isGroup = chat.type == ChatType.Group
    val isChannel = chat.type == ChatType.Channel
    val isBot = chat.type == ChatType.Bot
    val isSaved = chat.type == ChatType.Saved
    val isPrivate = chat.type == ChatType.Private
    val isUserChat = isPrivate || isBot
    val rights = chat.rights
    val canInvite = rights?.inviteUsers == true
    val name = if (isSaved) chat.title else user?.name?.ifBlank { null } ?: chat.title
    val photoPeer = userId ?: chat.id
    val scope = rememberCoroutineScope()

    LaunchedEffect(chatId) {
        repo.loadChatInfo(chatId)
        repo.loadProfileDetails(chatId)
        repo.loadProfilePhotos(chatId)
        repo.loadSharedCounts(chatId)
        repo.loadProfileGifts(chatId)
        repo.loadProfileStories(chatId)
        if (isPrivate && userId != null) repo.loadCommonGroups(userId)
    }
    val info = repo.chatInfo(chatId)
    val details = repo.profileDetails(chatId)
    val photos = if (isSaved) emptyList() else repo.profilePhotos(chatId)
    val gifts = repo.profileGifts(chatId)
    val posts = if (isSaved) emptyList() else repo.profileStories(chatId)
    val commonGroups = if (isPrivate && userId != null) repo.commonGroups(userId) else emptyList()
    val smallAvatar = if (isSaved) null else repo.avatar(photoPeer)
    val hasPhoto = !isSaved && (smallAvatar != null || photos.any { it.image != null })
    val colorId = details?.profileColorId ?: -1
    val baseColor = if (isSaved || isGroup) null else profileColor(colorId, c.isDark)
    val baseStyle = if (baseColor != null) HeaderStyle.Colored else HeaderStyle.Plain
    val isDeleted = user != null && user.firstName == "Deleted Account" && user.lastName.isEmpty()
    val isContact = isPrivate && userId != null && repo.isContact(userId)
    val canEditContact = isPrivate && userId != null && userId != repo.me.id && userId != 777000L && !isDeleted

    // ---- Header state (pull to expand) ----
    val listState = rememberLazyListState()
    val grid = remember(chatId) { MediaGridState(scope, listState) }
    var forwardIds by remember { mutableStateOf<List<Long>?>(null) }
    val header = remember { HeaderState(scope) { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    header.thresholdPx = with(density) { 64.dp.toPx() }
    header.maxPullPx = with(density) { 220.dp.toPx() }
    // Like Telegram iOS the page always opens on the round avatar; the full-width photo only appears when
    // the user pulls the page down (HeaderState expands past the threshold) — never by itself.
    androidx.compose.runtime.SideEffect { header.canExpand = hasPhoto }
    LaunchedEffect(hasPhoto) {
        if (!hasPhoto) header.setExpanded(false)
    }
    // Everything driven by the pull / expand gesture is read through these providers in layout, layer and draw
    // lambdas (or a leaf composable), so a pull frame does not recompose this whole screen.
    val pNow = remember(header) { { header.expand.value.coerceIn(0f, 1f) } }
    var photoIndex by remember { mutableIntStateOf(0) }
    var viewerOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var qrLink by remember { mutableStateOf<String?>(null) }

    // ---- Palette ----
    val plain = plainPalette(c)
    val basePalette = baseColor?.let { tintedPalette(it) } ?: plain
    val smallBitmap = rememberFileImage(smallAvatar?.let { repo.filePath(it) }, 64)
    val photoPage = rememberPhotoPageColor(smallBitmap, avatarColors(photoPeer).second)
    // Over the photo the buttons are light frosted glass (white tint over the blurred picture), as in the
    // iOS 26 reference — on a colored page they are a darker tint of the color instead.
    val photoPalette = tintedPalette(photoPage).copy(
        button = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f),
        buttonSolid = androidx.compose.ui.graphics.lerp(photoPage, androidx.compose.ui.graphics.Color.White, 0.18f),
    )
    val basePaletteS = rememberUpdatedState(basePalette)
    val photoPaletteS = rememberUpdatedState(photoPalette)
    val baseStyleS = rememberUpdatedState(baseStyle)
    val paletteState = remember(header) { derivedStateOf { basePaletteS.value.lerpTo(photoPaletteS.value, header.expand.value.coerceIn(0f, 1f)) } }
    val paletteNow = remember(paletteState) { { paletteState.value } }
    val tintedState = remember(header) { derivedStateOf { baseStyleS.value == HeaderStyle.Colored || header.expand.value.coerceIn(0f, 1f) > 0.5f } }

    // ---- Actions ----
    fun openChat() {
        val below = nav.stack.getOrNull(nav.stack.size - 2)?.route
        if (below == Route.Chat(chat.id)) nav.pop() else nav.push(Route.Chat(chat.id))
    }
    fun searchInChat() {
        com.abtin.tglass.features.chat.ChatSearchRequest.chatId = chat.id
        val below = nav.stack.getOrNull(nav.stack.size - 2)?.route
        if (below == Route.Chat(chat.id)) nav.pop() else nav.replaceTop(Route.Chat(chat.id))
    }
    fun leaveOrDelete() {
        val noun = if (isChannel) "Channel" else "Group"
        sheet.show(SheetRequest(actions = listOfNotNull(
            SheetAction("Leave $noun", destructive = true) { repo.deleteChat(chat.id); nav.resetTo(Route.Main) },
            if (rights?.owner == true) SheetAction("Delete $noun for everyone", destructive = true) {
                repo.deleteChatForAll(chat.id) { err -> if (err != null) sheet.showError(err) else nav.resetTo(Route.Main) }
            } else null,
        )))
    }
    fun toggleBlock() {
        val title = chat.title
        if (repo.isBlocked(chat.id)) {
            repo.setBlocked(chat.id, false) { err ->
                if (err != null) sheet.showError(err)
                else if (isBot) { repo.startBot(chat.id); toast.show("Bot restarted") }
                else toast.show("$title unblocked")
            }
        } else {
            sheet.show(SheetRequest(
                title = if (isBot) "Stop and block $title?" else "Block $title?",
                message = if (isBot) "The bot won't be able to send you messages." else "$title won't be able to message or call you.",
                actions = listOf(SheetAction(if (isBot) "Stop Bot" else "Block User", destructive = true) {
                    repo.setBlocked(chat.id, true) { err -> if (err != null) sheet.showError(err) else toast.show("$title blocked") }
                }),
            ))
        }
    }
    fun confirmClearHistory() {
        val o = repo.clearHistoryOptions(chat.id)
        val first = chat.title.substringBefore(' ')
        val done = { toast.show("History cleared") }
        val actions = listOfNotNull(
            if (o.forEveryone) SheetAction(if (isPrivate) "Delete for me and $first" else "Delete for Everyone", destructive = true) {
                repo.clearHistory(chat.id, true); done()
            } else null,
            if (o.forMe) SheetAction(if (o.forEveryone) "Delete just for me" else "Clear History", destructive = true) {
                repo.clearHistory(chat.id, false); done()
            } else null,
        )
        if (actions.isEmpty()) {
            sheet.alert("Can't Clear History", "The history of this chat can't be cleared.")
            return
        }
        sheet.show(SheetRequest(title = "Are you sure you want to delete all messages in this chat?", message = "This action cannot be undone.", actions = actions))
    }
    fun confirmReportSpam() {
        sheet.show(SheetRequest(
            title = "Report this ${if (isChannel) "channel" else "group"} as spam?",
            actions = listOf(SheetAction("Report Spam", destructive = true) {
                repo.reportSpam(chat.id) { err -> if (err != null) sheet.showError(err) else toast.show("Thank you! Your report will be reviewed by our team.") }
            }),
        ))
    }
    val publicLink: String? = run {
        val l = if (isUserChat) user?.username else (info?.link ?: chat.username)
        when {
            l.isNullOrBlank() -> null
            l.startsWith("http") -> l
            else -> "https://t.me/$l"
        }
    }
    fun call(video: Boolean) {
        user?.let { com.abtin.tglass.features.calls.requestCall(context, repo, nav, sheet, toast, it.id, video = video) }
    }
    fun more() {
        sheet.show(SheetRequest(actions = listOfNotNull(
            if (canEditContact && !isContact) SheetAction("Add to Contacts") { editOpen = true } else null,
            if (canEditContact && isContact) SheetAction("Edit Contact") { editOpen = true } else null,
            if (rights?.changeInfo == true && (isGroup || isChannel)) SheetAction(if (isChannel) "Edit Channel" else "Edit Group") { nav.push(Route.EditChat(chat.id)) } else null,
            if (canInvite && (isGroup || isChannel)) SheetAction(if (isChannel) "Add Subscribers" else "Add Members") { nav.push(Route.AddMembers(chat.id)) } else null,
            if (!repo.isLive || (repo.sharedCount(chatId, SharedKind.Media) ?: 0) > 0) SheetAction("Select") { grid.selectRequest = true } else null,
            SheetAction("Search Messages") { searchInChat() },
            publicLink?.let { l -> SheetAction(if (isUserChat) "Share Contact" else "Share Link") { shareText(context, l) } },
            publicLink?.let { l -> SheetAction("QR Code") { qrLink = l } },
            if (isUserChat) {
                val blocked = repo.isBlocked(chat.id)
                SheetAction(
                    when {
                        blocked && isBot -> "Restart Bot"
                        blocked -> "Unblock User"
                        isBot -> "Stop Bot"
                        else -> "Block User"
                    },
                    destructive = !blocked,
                ) { toggleBlock() }
            } else null,
            if ((isGroup || isChannel) && repo.canReportSpam(chat.id)) SheetAction("Report Spam", destructive = true) { confirmReportSpam() } else null,
            SheetAction("Clear History", destructive = true) { confirmClearHistory() },
        )))
    }
    val muteAction = ProfileAction(if (chat.muted) TgIcons.PiUnmute else TgIcons.PiMute, if (chat.muted) "Unmute" else "Mute") {
        com.abtin.tglass.features.groups.toggleMuteWithOptions(sheet, repo, chat.id)
    }
    val searchAction = ProfileAction(TgIcons.PiSearch, "Search") { searchInChat() }
    val moreAction = ProfileAction(TgIcons.PiMore, "More") { more() }
    val actions = buildList {
        when {
            isSaved -> { add(searchAction); add(moreAction) }
            isPrivate -> {
                if (details?.canCall != false) add(ProfileAction(TgIcons.PiCall, "Call") { call(false) })
                if (details?.canCall != false && details?.videoCalls != false) add(ProfileAction(TgIcons.PiVideo, "Video") { call(true) })
                add(muteAction); add(searchAction); add(moreAction)
            }
            isBot -> { add(ProfileAction(TgIcons.PiMessage, "Message") { openChat() }); add(muteAction); add(searchAction); add(moreAction) }
            else -> {
                add(muteAction); add(searchAction)
                if (chat.joined) add(ProfileAction(TgIcons.PiLeave, "Leave") { leaveOrDelete() })
                else add(ProfileAction(TgIcons.PiAddMember, "Join") { repo.joinChat(chat.id) })
                add(moreAction)
            }
        }
    }

    // ---- Profile music ----
    val music = details?.music
    val player = com.abtin.tglass.core.media.VoicePlayer
    val musicKey = "profile-music:$photoPeer"
    var musicPending by remember { mutableStateOf(false) }
    val musicPath = music?.file?.let { repo.filePath(it) }
    LaunchedEffect(musicPending, musicPath) {
        if (musicPending && musicPath != null) {
            musicPending = false
            player.toggle(context, musicKey, musicPath, music?.duration ?: 0)
        }
    }
    fun playMusic() {
        val m = music ?: return
        val f = m.file
        when {
            f == null -> sheet.alert("Demo Mode", "Profile music plays when you sign in with a real Telegram account.")
            musicPath != null -> player.toggle(context, musicKey, musicPath, m.duration)
            else -> { musicPending = true; repo.requestImage(f) }
        }
    }

    // ---- Tabs ----
    fun has(kind: SharedKind) = (repo.sharedCount(chatId, kind) ?: 0) > 0
    val tabs = buildList {
        if (posts.isNotEmpty() && !isGroup) add(ProfileTab.Posts)
        if (isGroup) add(ProfileTab.Members)
        if (posts.isNotEmpty() && isGroup) add(ProfileTab.Posts)
        if (gifts.isNotEmpty()) add(ProfileTab.Gifts)
        if (has(SharedKind.Media) || !repo.isLive) add(ProfileTab.Media)
        if (has(SharedKind.Files)) add(ProfileTab.Files)
        if (has(SharedKind.Links)) add(ProfileTab.Links)
        if (has(SharedKind.Music)) add(ProfileTab.Music)
        if (has(SharedKind.Voice)) add(ProfileTab.Voice)
        if (has(SharedKind.Gifs)) add(ProfileTab.Gifs)
        if (commonGroups.isNotEmpty()) add(ProfileTab.Groups)
    }
    var tabKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = tabs.indexOfFirst { it.name == tabKey }.takeIf { it >= 0 } ?: 0
    val currentTab = tabs.getOrNull(selected)
    LaunchedEffect(chatId, currentTab) {
        when (currentTab) {
            ProfileTab.Media -> repo.loadSharedMedia(chatId, MediaKind.Media)
            ProfileTab.Files -> repo.loadSharedMedia(chatId, MediaKind.Files)
            ProfileTab.Links -> repo.loadSharedMedia(chatId, MediaKind.Links)
            ProfileTab.Voice -> repo.loadSharedMedia(chatId, MediaKind.Voice)
            ProfileTab.Gifs -> repo.loadSharedMedia(chatId, MediaKind.Gifs)
            ProfileTab.Music -> repo.loadSharedMusic(chatId)
            else -> Unit
        }
    }
    LaunchedEffect(grid.selectRequest, tabs.size) {
        if (grid.selectRequest && tabs.contains(ProfileTab.Media)) {
            grid.selectRequest = false
            tabKey = ProfileTab.Media.name
            grid.selected.clear()
            grid.selecting = true
        }
    }
    LaunchedEffect(currentTab) { if (currentTab != ProfileTab.Media && grid.selecting) grid.clear() }
    androidx.activity.compose.BackHandler(enabled = grid.selecting) { grid.clear() }
    val slide = remember { Animatable(0f) }
    val pill = remember { Animatable(0f) }
    LaunchedEffect(selected, tabs.size) { if (!pill.isRunning && slide.value == 0f) pill.snapTo(selected.toFloat()) }
    val tabsState = rememberUpdatedState(tabs)
    val selectedState = rememberUpdatedState(selected)

    val backdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(paletteState.value.page) }
                .pointerInput(Unit) {
                    // Safety net: when the finger lifts, spring back any pull-down overscroll.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Final)
                        } while (e.changes.any { it.pressed })
                        header.release()
                    }
                },
        ) {
            val width = maxWidth
            val widthPx = with(density) { width.toPx() }.coerceAtLeast(1f)
            val widthS = rememberUpdatedState(width)
            val statusTopS = rememberUpdatedState(statusTop)
            val densityS = rememberUpdatedState(density)
            val hasMusicS = rememberUpdatedState(music != null)
            val geoState = remember(header) {
                derivedStateOf {
                    headerGeometry(
                        widthS.value, statusTopS.value, header.expand.value.coerceIn(0f, 1f),
                        with(densityS.value) { header.pull.toDp() }, hasMusicS.value,
                    )
                }
            }
            val geoNow = remember(geoState) { { geoState.value } }
            val barBottomPx = with(density) { (statusTop + 52.dp).toPx() }
            val pinYPx = with(density) { (statusTop + 60.dp).toPx() }.roundToInt()

            fun selectTab(i: Int, fromDrag: Boolean) {
                val list = tabsState.value
                val cur = selectedState.value
                if (i !in list.indices) return
                if (i == cur) {
                    scope.launch { slide.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f)) }
                    scope.launch { pill.animateTo(cur.toFloat(), spring(dampingRatio = 0.72f, stiffness = 360f)) }
                    return
                }
                val dir = if (i > cur) 1f else -1f
                tabKey = list[i].name
                scope.launch {
                    slide.snapTo(if (fromDrag) slide.value + dir * widthPx else dir * widthPx * 0.35f)
                    slide.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f))
                }
                scope.launch { pill.animateTo(i.toFloat(), spring(dampingRatio = 0.72f, stiffness = 360f)) }
            }
            fun endDrag() {
                val cur = selectedState.value
                val v = slide.value
                when {
                    v < -widthPx * 0.22f && cur + 1 < tabsState.value.size -> selectTab(cur + 1, true)
                    v > widthPx * 0.22f && cur > 0 -> selectTab(cur - 1, true)
                    else -> selectTab(cur, true)
                }
            }
            // Tab content follows the finger horizontally and slides in when the tab changes.
            val itemModifier = Modifier
                .graphicsLayer {
                    translationX = slide.value
                    alpha = 1f - (abs(slide.value) / widthPx * 0.9f).coerceIn(0f, 0.85f)
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(onDragEnd = { endDrag() }, onDragCancel = { endDrag() }) { change, dx ->
                        change.consume()
                        val cur = selectedState.value
                        val edge = (dx > 0 && cur == 0) || (dx < 0 && cur >= tabsState.value.size - 1)
                        val next = slide.value + if (edge) dx * 0.3f else dx
                        scope.launch {
                            slide.snapTo(next)
                            pill.snapTo(cur - next / widthPx)
                        }
                    }
                }

            val musicLine: (@Composable () -> Unit)? = if (music != null) {
                { MusicLine(music, player.currentKey == musicKey && player.playing, paletteState.value) { playMusic() } }
            } else null
            val collapse by remember {
                derivedStateOf {
                    val nameTopPx = with(densityS.value) { geoState.value.nameTop.toPx() }.coerceAtLeast(1f)
                    if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / nameTopPx).coerceIn(0f, 1f)
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .mediaPinch(grid, currentTab == ProfileTab.Media)
                    .nestedScroll(header.connection)
                    .layerBackdrop(backdrop)
                    .drawBehind { drawRect(paletteState.value.page) },
                state = listState,
                contentPadding = PaddingValues(bottom = navBottom + 40.dp),
            ) {
                item(key = "header") {
                    val (sub, online) = profileSubtitle(chat, user, info, repo)
                    ProfileHeaderItem(
                        geo = geoNow,
                        width = width,
                        p = pNow,
                        style = baseStyle,
                        palette = paletteNow,
                        name = name,
                        seed = chat.id,
                        photoPeer = photoPeer,
                        saved = isSaved,
                        subtitle = sub,
                        online = online,
                        badges = { NameBadges(details, chat.verified || user?.verified == true, user?.premium == true, paletteState.value, tintedState.value) },
                        photo = (photos.getOrNull(photoIndex) ?: photos.firstOrNull())?.image,
                        photoCount = photos.size,
                        photoIndex = photoIndex,
                        gifts = gifts.filter { it.pinned }.ifEmpty { gifts },
                        musicLine = musicLine,
                        onAvatarTap = { xf ->
                            if (pNow() > 0.5f && photos.size > 1 && (xf < 0.3f || xf > 0.7f)) {
                                photoIndex = if (xf < 0.3f) (photoIndex - 1).coerceAtLeast(0) else (photoIndex + 1).coerceAtMost(photos.size - 1)
                            } else if (!isSaved) viewerOpen = true
                        },
                    )
                }
                item(key = "info") {
                    ProfileInfoBlock(
                        chat = chat, user = user, info = info, details = details, palette = paletteState.value,
                        publicLink = publicLink,
                        onQr = { qrLink = it },
                        onOpenChat = { id -> nav.push(Route.Chat(id)) },
                    )
                }
                if (tabs.isNotEmpty()) {
                    item(key = "tabs") { Spacer(Modifier.fillMaxWidth().height(52.dp)) }
                    profileTabContent(
                        tab = currentTab,
                        chat = chat,
                        repo = repo,
                        info = info,
                        gifts = gifts,
                        posts = posts,
                        commonGroups = commonGroups,
                        palette = paletteState,
                        itemModifier = itemModifier,
                        grid = grid,
                        onOpenMedia = { m -> nav.push(Route.Media(chat.id, m.id)) },
                        onGift = { g -> showGift(sheet, g) },
                        onOpenPost = { i -> posts.getOrNull(i)?.let { nav.push(Route.Stories(it.userId, postsOf = chat.id, startIndex = i)) } },
                        onOpenChat = { id -> nav.push(Route.Chat(id)) },
                        onOpenUser = { id -> nav.push(Route.UserProfile(id)) },
                        onAddMembers = { if (canInvite) nav.push(Route.AddMembers(chat.id)) else sheet.alert("Admin Rights Required", "Only admins can add members to this group.") },
                        onRemoveMember = { u ->
                            sheet.show(SheetRequest(title = u.name, actions = listOf(
                                SheetAction("Remove from Group", destructive = true) {
                                    repo.removeMember(chat.id, u.id) { err -> if (err != null) sheet.showError(err) else toast.show("${u.name} removed") }
                                },
                            )))
                        },
                    )
                }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }

            ProfileTopBar(
                paletteState = paletteState,
                center = { CollapsedTitle(name, chat.id, { collapse }, saved = isSaved, photoPeer = photoPeer) },
                right = {
                    when {
                        grid.selecting -> GlassTextButton("Cancel", { grid.clear() }, bold = true)
                        canEditContact && isContact -> GlassTextButton("Edit", { editOpen = true })
                        canEditContact -> GlassTextButton("Add Contact", { editOpen = true })
                        rights?.changeInfo == true && (isGroup || isChannel) -> GlassTextButton("Edit", { nav.push(Route.EditChat(chat.id)) })
                    }
                },
            )

            // Glass circle buttons: above the list (outside its backdrop layer), tracking the header.
            val buttonsY by remember {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0) -10_000
                    else (with(densityS.value) { geoState.value.buttonsTop.toPx() } - listState.firstVisibleItemScrollOffset).roundToInt()
                }
            }
            val buttonsAlpha by remember(barBottomPx) {
                derivedStateOf { ((buttonsY - barBottomPx) / (with(density) { 36.dp.toPx() })).coerceIn(0f, 1f) }
            }
            // Booleans, so the screen recomposes when the buttons appear / become tappable, not on every scroll frame.
            val buttonsShown by remember { derivedStateOf { buttonsAlpha > 0f } }
            val buttonsEnabled by remember { derivedStateOf { buttonsAlpha > 0.5f } }
            if (actions.isNotEmpty() && buttonsShown) {
                ProfileActionsOverlay(actions, paletteState, y = { buttonsY }, alpha = { buttonsAlpha }, enabled = buttonsEnabled)
            }

            // Selection bar: an overlay above the list (outside its backdrop layer).
            if (grid.selecting) {
                val chosen = repo.sharedMedia(chatId, MediaKind.Media).filter { it.id in grid.selected }
                val delivered = chosen.map { it.id }
                MediaSelectionBar(
                    count = grid.selected.size,
                    canForward = delivered.isNotEmpty(),
                    canShow = delivered.size == 1 && grid.selected.size == 1,
                    canDelete = grid.selected.isNotEmpty(),
                    onForward = { forwardIds = delivered },
                    onShow = {
                        ChatJumpRequest.request(chat.id, delivered.first())
                        grid.clear()
                        openChat()
                    },
                    onDelete = {
                        if (chosen.isEmpty()) {
                            sheet.alert("Demo Mode", "Sample media can't be deleted.")
                        } else {
                            val ids = chosen.map { it.id }.toSet()
                            sheet.show(deleteAlert(repo, chat, chosen, null) {
                                repo.forgetSharedMessages(chat.id, ids)
                                grid.clear()
                            })
                        }
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = navBottom + 8.dp),
                )
            }
            forwardIds?.let { ids ->
                ForwardSheet(fromChatId = chat.id, messageIds = ids, onDismiss = { forwardIds = null }, onDone = { forwardIds = null; grid.clear() })
            }

            // Sticky tab switcher: follows its placeholder, then pins under the top bar.
            if (tabs.isNotEmpty()) {
                val tabsY by remember(pinYPx) {
                    derivedStateOf {
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "tabs" }
                        when {
                            item != null -> maxOf(item.offset, pinYPx)
                            listState.firstVisibleItemIndex > 2 -> pinYPx
                            else -> null
                        }
                    }
                }
                val pinned by remember(pinYPx) {
                    derivedStateOf {
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "tabs" }
                        (item == null && listState.firstVisibleItemIndex > 2) || (item != null && item.offset < pinYPx)
                    }
                }
                val tabsShown by remember(pinYPx) { derivedStateOf { tabsY != null } }
                val pinnedPaletteS = rememberUpdatedState(plain.copy(tab = c.secondaryText, tabSelected = c.text))
                val tabBarPalette = remember(pinYPx) { derivedStateOf { if (pinned) pinnedPaletteS.value else paletteState.value } }
                if (tabsShown) {
                    ProfileTabBar(
                        tabs = tabs,
                        selected = selected,
                        position = { pill.value },
                        paletteState = tabBarPalette,
                        pinned = pinned,
                        giftIcons = gifts,
                        onSelect = { selectTab(it, false) },
                        modifier = Modifier.offset { IntOffset(0, (tabsY ?: 0) + 4.dp.roundToPx()) },
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = viewerOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ProfilePhotoViewer(
                    name = name,
                    seed = photoPeer,
                    photos = photos,
                    startIndex = photoIndex,
                    onIndex = { photoIndex = it },
                    onDismiss = { viewerOpen = false },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = editOpen && user != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                if (user != null) {
                    EditContactPage(
                        user = user,
                        isContact = isContact,
                        onClose = { editOpen = false },
                        onDeleted = { editOpen = false },
                    )
                }
            }
            val link = qrLink
            androidx.compose.animation.AnimatedVisibility(visible = link != null, enter = fadeIn(), exit = fadeOut()) {
                if (link != null) {
                    androidx.activity.compose.BackHandler { qrLink = null }
                    QrOverlay(
                        link = link,
                        title = name,
                        seed = photoPeer,
                        onShare = { shareText(context, link) },
                        onCopy = { copyText(context, "link", link); toast.show("Link copied") },
                        onDismiss = { qrLink = null },
                    )
                }
            }
        }
    }
}

private fun showGift(sheet: com.abtin.tglass.ui.components.ActionSheetState, g: ProfileGift) {
    val lines = listOfNotNull(
        g.senderName?.let { "From $it" },
        g.message,
        if (g.stars > 0) "Worth ⭐ ${g.stars}" else null,
        if (g.date > 0) "Received ${formatDay(g.date * 1000L)}" else null,
    )
    sheet.show(SheetRequest(title = g.title ?: "Gift", message = lines.joinToString("\n").ifBlank { null }, actions = emptyList(), cancel = "Close"))
}

private data class InfoLine(val label: String, val value: String, val accent: Boolean = false, val userText: Boolean = false, val kind: String = "")

/** Personal channel card + the rounded info card (phone, username, bio, location, birthday / link, description). */
@Composable
private fun ProfileInfoBlock(
    chat: Chat,
    user: User?,
    info: ChatInfo?,
    details: com.abtin.tglass.data.ProfileDetails?,
    palette: ProfilePalette,
    publicLink: String?,
    onQr: (String) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val nav = LocalNavigator.current
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        val personal = details?.personalChatId?.takeIf { it != 0L }?.let { repo.chat(it) }
        if (personal != null) {
            val last = repo.lastMessage(personal.id)?.preview
            val sub = last ?: if (personal.members > 0) "${formatCount(personal.members)} subscribers" else "channel"
            ChannelCard(personal.title, sub, personal.id, palette) { onOpenChat(personal.id) }
            Spacer(Modifier.height(12.dp))
        }
        val rows = buildList {
            if (user != null && (chat.type == ChatType.Private || chat.type == ChatType.Bot)) {
                if (user.phone.isNotBlank()) add(InfoLine("phone", user.phone, kind = "phone"))
                user.username?.let { add(InfoLine("username", "@$it", accent = true, kind = "username")) }
                val bio = info?.about ?: user.bio
                if (!bio.isNullOrBlank()) add(InfoLine("bio", bio, userText = true))
                details?.botDescription?.takeIf { it != bio }?.let { add(InfoLine("info", it, userText = true)) }
                details?.businessAddress?.let { add(InfoLine("location", it, userText = true, kind = "location")) }
                details?.businessHours?.let { add(InfoLine("business hours", it)) }
                details?.birthday?.let { b -> add(InfoLine("date of birth", details?.age?.let { a -> "$b ($a years old)" } ?: b, kind = "birthday")) }
            } else if (chat.type != ChatType.Saved) {
                publicLink?.let { add(InfoLine(if (chat.username != null) "share link" else "invite link", it.removePrefix("https://"), accent = true, kind = "link")) }
                val about = info?.about ?: chat.description
                if (!about.isNullOrBlank()) add(InfoLine(if (chat.type == ChatType.Channel) "description" else "info", about, userText = true))
            }
        }
        if (rows.isNotEmpty()) {
            InfoCard(palette) {
                rows.forEachIndexed { i, r ->
                    val divider = i != rows.lastIndex
                    when (r.kind) {
                        "phone" -> InfoRow(r.label, r.value, palette, divider, onClick = {
                            sheet.show(SheetRequest(title = r.value, actions = listOfNotNull(
                                user?.let { u -> SheetAction("Telegram Call") { com.abtin.tglass.features.calls.requestCall(context, repo, nav, sheet, toast, u.id, video = false) } },
                                SheetAction("Copy Phone Number") { copyText(context, "phone", r.value); toast.show("Phone number copied") },
                            )))
                        })
                        "username", "link" -> InfoRow(
                            r.label, r.value, palette, divider, accent = r.accent,
                            trailing = { publicLink?.let { l -> QrButton(palette) { onQr(l) } } },
                            onClick = { publicLink?.let { l -> copyText(context, "link", l); toast.show("Link copied") } },
                        )
                        "location" -> InfoRow(r.label, r.value, palette, divider, userText = true, trailing = { MiniMap() }, onClick = {
                            copyText(context, "address", r.value); toast.show("Address copied")
                        })
                        "birthday" -> InfoRow(r.label, r.value, palette, divider, onLongClick = {
                            copyText(context, r.label, r.value); toast.show("Copied")
                        })
                        else -> InfoRow(r.label, r.value, palette, divider, userText = r.userText, onLongClick = {
                            copyText(context, r.label, r.value); toast.show("Copied")
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Items of the selected tab. */
private fun androidx.compose.foundation.lazy.LazyListScope.profileTabContent(
    tab: ProfileTab?,
    chat: Chat,
    repo: TelegramRepository,
    info: ChatInfo?,
    gifts: List<ProfileGift>,
    posts: List<com.abtin.tglass.data.Story>,
    commonGroups: List<Long>,
    palette: State<ProfilePalette>,
    itemModifier: Modifier,
    grid: MediaGridState,
    onOpenMedia: (Message) -> Unit,
    onGift: (ProfileGift) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onAddMembers: () -> Unit,
    onRemoveMember: (User) -> Unit,
) {
    when (tab) {
        null -> Unit
        ProfileTab.Members -> {
            val members = info?.members.orEmpty().mapNotNull { m -> repo.user(m.userId)?.let { it to m.role } }
            item(key = "members") {
                InfoCard(palette.value, itemModifier) {
                    com.abtin.tglass.ui.components.Cell(
                        "Add Members", icon = TgIcons.PiAddMember, iconColor = TgTheme.colors.accent, titleColor = TgTheme.colors.accent,
                        chevron = false, divider = members.isNotEmpty(), onClick = onAddMembers,
                    )
                    val rights = chat.rights
                    members.forEachIndexed { i, (u, role) ->
                        val removable = rights?.banMembers == true && u.id != repo.me.id && role != "owner" && (role != "admin" || rights?.owner == true)
                        com.abtin.tglass.features.groups.MemberCell(
                            u, role = role, divider = i != members.lastIndex,
                            onClick = { onOpenUser(u.id) },
                            onLongClick = if (!removable) null else ({ onRemoveMember(u) }),
                        )
                    }
                }
            }
        }
        ProfileTab.Posts -> postsGrid(posts, itemModifier, onOpenPost)
        ProfileTab.Gifts -> giftGrid(gifts, itemModifier, onGift)
        ProfileTab.Media, ProfileTab.Gifs -> {
            val kind = if (tab == ProfileTab.Media) MediaKind.Media else MediaKind.Gifs
            val tiles = repo.sharedMedia(chat.id, kind).mapNotNull { m -> (m.content as? MessageContent.Photo)?.let { m to it } }
            val fillers = if (repo.isLive || tiles.isNotEmpty() || tab != ProfileTab.Media) emptyList()
            else (0 until 15).map { null to MessageContent.Photo(it + chat.id.toInt(), 1f, emoji = listOf("🏔", "🌅", "🐈", "🍜", "🌸", "🎨", "🌊")[it % 7], video = it % 4 == 1, duration = 14 + it * 7) }
            val all: List<Pair<Message?, MessageContent.Photo>> = tiles + fillers
            if (all.isEmpty()) emptyTab("empty-${tab.name}", tab.title, palette, itemModifier)
            else mediaGrid(tab.name, all, itemModifier, onOpenMedia, grid = if (tab == ProfileTab.Media) grid else null)
        }
        ProfileTab.Files, ProfileTab.Links, ProfileTab.Voice, ProfileTab.Music -> {
            val list = when (tab) {
                ProfileTab.Files -> repo.sharedMedia(chat.id, MediaKind.Files).filter { (it.content as? MessageContent.File)?.music != true }
                ProfileTab.Links -> repo.sharedMedia(chat.id, MediaKind.Links)
                ProfileTab.Voice -> repo.sharedMedia(chat.id, MediaKind.Voice)
                else -> repo.sharedMusic(chat.id)
            }
            if (list.isEmpty()) emptyTab("empty-${tab.name}", tab.title, palette, itemModifier)
            else item(key = "list-${tab.name}") {
                InfoCard(palette.value, itemModifier) {
                    list.forEachIndexed { i, m -> SharedRow(m, palette.value, divider = i != list.lastIndex) }
                }
            }
        }
        ProfileTab.Groups -> {
            val groups = commonGroups.mapNotNull { repo.chat(it) }
            if (groups.isEmpty()) emptyTab("empty-groups", "groups", palette, itemModifier)
            else item(key = "groups") {
                InfoCard(palette.value, itemModifier) {
                    groups.forEachIndexed { i, g ->
                        val sub = if (g.members > 0) "${formatCount(g.members)} members" else null
                        ChatRowCell(g.title, sub, g.id, palette.value, divider = i != groups.lastIndex) { onOpenChat(g.id) }
                    }
                }
            }
        }
    }
}

/** Opens (or creates) the private chat's profile for a user. */
@Composable
fun UserProfileScreen(userId: Long) {
    val repo = LocalRepository.current
    val chatId = remember(userId) { repo.privateChatWith(userId) }
    ProfileScreen(chatId)
}

/** Top bar in its own scope: the page color it fades into animates with the expansion without recomposing the screen. */
@Composable
private fun ProfileTopBar(
    paletteState: State<ProfilePalette>,
    center: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
    right: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    GlassTopBar(title = null, fade = paletteState.value.page, center = center, right = right)
}
