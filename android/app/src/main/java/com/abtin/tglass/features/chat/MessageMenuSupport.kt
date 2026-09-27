package com.abtin.tglass.features.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.abtin.tglass.core.media.Sharing
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageCaps
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.ReportStep
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.ui.components.ActionSheetState
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetCheckbox
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.ToastState
import kotlinx.coroutines.launch

/** The photo/video file behind a message (and whether it is a video), or null for other content. */
fun galleryFileOf(m: Message): Pair<ImageRef, Boolean>? {
    val p = m.content as? MessageContent.Photo ?: return null
    return if (p.video || p.loop) p.videoFile?.let { it to true } else p.image?.let { it to false }
}

/** Returns a function that saves a photo/video message to the gallery (asking for storage access on old Android). */
@Composable
fun rememberMediaSaver(repo: TelegramRepository, toast: ToastState): (Message) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    fun doSave(path: String, video: Boolean) {
        scope.launch {
            val ok = Sharing.saveToGallery(context, path, video)
            if (ok) toast.show(if (video) "Video saved to Gallery" else "Photo saved to Gallery")
            else toast.show("Couldn't save to the gallery", Icons.Rounded.ErrorOutline)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val p = pending
        pending = null
        if (granted && p != null) doSave(p.first, p.second)
        else if (!granted) toast.show("Allow storage access to save to the gallery", Icons.Rounded.ErrorOutline)
    }
    return { m ->
        val file = galleryFileOf(m)
        if (file == null) {
            toast.show(if (repo.isLive) "This media has no file" else "Sample media has no file", Icons.Rounded.ErrorOutline)
        } else {
            val (ref, video) = file
            val path = repo.filePath(ref)
            if (path == null) {
                repo.requestImage(ref)
                toast.show("Downloading… try again in a moment", Icons.Rounded.ErrorOutline)
            } else if (Sharing.saveNeedsPermission &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                pending = path to video
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                doSave(path, video)
            }
        }
    }
}

/**
 * iOS alert for deleting messages: "Delete message?" with an "Also delete for Anna" check in private chats,
 * "Delete for all members" for your own messages in groups, and a red Delete button.
 * [caps] (single message, when known) comes from TDLib and decides what is allowed.
 */
fun deleteAlert(
    repo: TelegramRepository,
    chat: Chat,
    messages: List<Message>,
    caps: MessageCaps?,
    onDone: () -> Unit,
): SheetRequest {
    val ids = messages.map { it.id }.toSet()
    val one = ids.size == 1
    val title = if (one) "Delete message?" else "Delete ${ids.size} messages?"
    val private = chat.type == ChatType.Private || chat.type == ChatType.Bot
    // What the server allows: from getMessageProperties for one message, otherwise Telegram's usual rules.
    val forAll: Boolean
    val forSelf: Boolean
    if (caps != null) {
        forAll = caps.canDeleteForAll
        forSelf = caps.canDeleteForSelf
    } else {
        forAll = when (chat.type) {
            ChatType.Saved -> false
            ChatType.Private, ChatType.Bot -> true
            ChatType.Channel -> true
            ChatType.Group -> messages.all { it.outgoing }
        }
        forSelf = chat.type != ChatType.Channel
    }
    val delete: (Boolean) -> Unit = { everyone -> repo.deleteMessages(chat.id, ids, forEveryone = everyone); onDone() }
    return when {
        // Both possible: let the user choose with a check, like Telegram iOS.
        forAll && forSelf -> {
            val firstName = chat.title.substringBefore(' ').ifBlank { chat.title }
            SheetRequest(
                title = title,
                message = if (one) "Are you sure you want to delete this message?" else "Are you sure you want to delete these messages?",
                alert = true,
                checkbox = SheetCheckbox(if (private) "Also delete for $firstName" else "Delete for all members", initial = private),
                actions = listOf(SheetAction("Delete", destructive = true, onClickChecked = { everyone -> delete(everyone) })),
            )
        }
        // Only for everyone (channels, supergroups).
        forAll -> SheetRequest(
            title = title,
            message = if (chat.type == ChatType.Channel) "This will delete it for all subscribers." else "This will delete it for all members.",
            alert = true,
            actions = listOf(SheetAction("Delete", destructive = true) { delete(true) }),
        )
        else -> SheetRequest(
            title = title,
            message = if (chat.type == ChatType.Saved) null else "This will delete it only for you.",
            alert = true,
            actions = listOf(SheetAction("Delete for Me", destructive = true) { delete(false) }),
        )
    }
}

/** Telegram's report flow for messages: pick a reason, then a confirmation toast. */
fun reportMessagesFlow(repo: TelegramRepository, sheet: ActionSheetState, toast: ToastState, chatId: Long, ids: List<Long>, optionId: ByteArray? = null) {
    repo.reportMessages(chatId, ids, optionId) { step ->
        when (step) {
            is ReportStep.Options -> sheet.show(
                SheetRequest(
                    title = step.title,
                    actions = step.options.map { o -> SheetAction(o.title) { reportMessagesFlow(repo, sheet, toast, chatId, ids, o.id) } },
                )
            )
            is ReportStep.Done -> toast.show("Thank you! Your report will be reviewed by our team.")
            is ReportStep.Failed -> toast.show(step.message, Icons.Rounded.ErrorOutline)
        }
    }
}
