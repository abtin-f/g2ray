package com.abtin.tglass.ui.components

/**
 * The one place user-facing errors and important notices go through. Everything is shown as the centered
 * iOS 26 glass alert of [ActionSheetHost] (title, message, OK) — never a toast — like Telegram for iPhone.
 * Brief success confirmations ("Link copied") stay toasts.
 *
 * TDLib errors arrive as raw codes ("PREMIUM_ACCOUNT_REQUIRED", "FLOOD_WAIT_42", "Not enough rights to …")
 * or already humanized sentences; [AppErrors.describe] turns both into a friendly title + message.
 */
class FriendlyError(val title: String, val message: String?)

object AppErrors {
    private fun seconds(raw: String): Int? =
        Regex("""(?:FLOOD_WAIT_|SLOWMODE_WAIT_|FLOOD_PREMIUM_WAIT_|retry after )(\d+)""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun wait(s: Int?): String = when {
        s == null || s <= 0 -> "a little while"
        s < 60 -> if (s == 1) "1 second" else "$s seconds"
        s < 3600 -> ((s + 59) / 60).let { if (it == 1) "1 minute" else "$it minutes" }
        else -> ((s + 3599) / 3600).let { if (it == 1) "1 hour" else "$it hours" }
    }

    /** Sentence case with a final period, for TDLib's own English messages ("Chat not found" → "Chat not found."). */
    private fun sentence(raw: String): String {
        val t = raw.trim().replaceFirstChar { it.uppercase() }
        return if (t.endsWith('.') || t.endsWith('!') || t.endsWith('?')) t else "$t."
    }

    fun describe(raw: String): FriendlyError {
        val e = raw.trim()
        val u = e.uppercase()
        return when {
            e.isEmpty() -> FriendlyError("Something Went Wrong", "Please try again.")
            // ---- Premium ----
            "PRIVACY_PREMIUM_REQUIRED" in u ->
                FriendlyError("Telegram Premium Required", "This user only accepts messages from contacts and Telegram Premium subscribers.")
            "PREMIUM_ACCOUNT_REQUIRED" in u || "PREMIUM_REQUIRED" in u || "REQUIRES PREMIUM" in u || "PREMIUM IS REQUIRED" in u ||
                "NEED PREMIUM" in u || "PREMIUM ACCOUNT" in u || "TELEGRAM PREMIUM" in u ->
                FriendlyError("Telegram Premium Required", "This feature is available only to subscribers of Telegram Premium.")
            "FLOOD_PREMIUM_WAIT" in u ->
                FriendlyError("Speed Limit", "Uploads and downloads are limited right now. Try again in ${wait(seconds(e))}, or subscribe to Telegram Premium for unlimited speed.")
            // ---- Limits (iOS shows these with an offer to raise them with Premium) ----
            "CHANNELS_TOO_MUCH" in u || "TOO MANY CHANNELS" in u ->
                FriendlyError("Too Many Groups and Channels", "You are a member of too many groups and channels. Leave some before joining new ones, or subscribe to Telegram Premium to double the limit.")
            "PINNED_DIALOGS_TOO_MUCH" in u || "PINNED_TOO_MUCH" in u ->
                FriendlyError("Limit Reached", "You can't pin more chats. Unpin some first, or subscribe to Telegram Premium to double the limit.")
            "DIALOG_FILTERS_TOO_MUCH" in u || "FILTER_INCLUDE_TOO_MUCH" in u || "CHATLISTS_TOO_MUCH" in u ->
                FriendlyError("Limit Reached", "You've reached the limit of chat folders or chats in a folder. Subscribe to Telegram Premium to double the limit.")
            "CHANNELS_ADMIN_PUBLIC_TOO_MUCH" in u ->
                FriendlyError("Too Many Public Links", "You've reserved too many public links. Free one up by making another group or channel private.")
            "USERNAMES_ACTIVE_TOO_MUCH" in u ->
                FriendlyError("Limit Reached", "You have too many active usernames.")
            // ---- Waiting ----
            "SLOWMODE_WAIT" in u ->
                FriendlyError("Slow Mode", "Slow mode is enabled in this chat. You can send your next message in ${wait(seconds(e))}.")
            "FLOOD_WAIT" in u || "TOO MANY REQUESTS" in u || "TOO MANY ATTEMPTS" in u ->
                FriendlyError("Too Many Attempts", "Please try again in ${wait(seconds(e))}.")
            "PEER_FLOOD" in u ->
                FriendlyError("Limit Reached", "Sorry, you can only send messages to mutual contacts at the moment.")
            // ---- Sign in ----
            "PHONE_NUMBER_INVALID" in u || "INVALID PHONE NUMBER" in u ->
                FriendlyError("Invalid Phone Number", "Please check the number and try again.")
            "PHONE_NUMBER_BANNED" in u || "NUMBER IS BANNED" in u ->
                FriendlyError("Phone Number Banned", "This phone number is banned from Telegram.")
            "PHONE_CODE_INVALID" in u || "INVALID CODE" in u ->
                FriendlyError("Invalid Code", "Please check the code and try again.")
            "PHONE_CODE_EXPIRED" in u || "CODE HAS EXPIRED" in u ->
                FriendlyError("Code Expired", "The code has expired. Please request a new one.")
            "PASSWORD_HASH_INVALID" in u || "INVALID PASSWORD" in u ->
                FriendlyError("Incorrect Password", "The password you entered is incorrect. Please try again.")
            "API_ID" in u || "API ID" in u ->
                FriendlyError("Invalid API ID", "Your API ID or API hash is not valid. Check them on my.telegram.org.")
            // ---- Privacy / permissions ----
            "USER_PRIVACY_RESTRICTED" in u ->
                FriendlyError("Privacy Restrictions", "Sorry, this user's privacy settings don't allow you to do this.")
            "USER_NOT_MUTUAL_CONTACT" in u ->
                FriendlyError("Not a Mutual Contact", "This user can only be added by their mutual contacts.")
            "USER_IS_BLOCKED" in u || "YOU_BLOCKED_USER" in u || "USER_BLOCKED" in u ->
                FriendlyError("User Blocked", "You can't message this user while one of you has blocked the other.")
            "USER_BANNED_IN_CHANNEL" in u || "USER_RESTRICTED" in u ->
                FriendlyError("Restricted", "You are not allowed to do this in public groups and channels right now.")
            "CHAT_SEND_" in u && "FORBIDDEN" in u -> {
                val what = when {
                    "PHOTO" in u -> "photos"
                    "ROUNDVIDEO" in u -> "video messages"
                    "VIDEO" in u -> "videos"
                    "STICKER" in u -> "stickers"
                    "GIF" in u -> "GIFs"
                    "VOICE" in u -> "voice messages"
                    "POLL" in u -> "polls"
                    "DOC" in u -> "files"
                    "AUDIO" in u -> "music"
                    "PLAIN" in u -> "text messages"
                    else -> "media"
                }
                FriendlyError("Not Allowed", "Sending $what isn't allowed in this chat.")
            }
            "CHAT_WRITE_FORBIDDEN" in u || "HAVE NO WRITE ACCESS" in u || "NO RIGHTS TO SEND" in u ->
                FriendlyError("Not Allowed", "You can't send messages in this chat.")
            "CHAT_ADMIN_REQUIRED" in u || "RIGHT_FORBIDDEN" in u || "NOT ENOUGH RIGHTS" in u || "HAVE NO RIGHTS" in u ->
                FriendlyError("Admin Rights Required", "You don't have permission to do this in this chat.")
            "USER_ALREADY_PARTICIPANT" in u -> FriendlyError("Already a Member", "You are already a member of this chat.")
            "INVITE_HASH_EXPIRED" in u || "INVITE_HASH_INVALID" in u ->
                FriendlyError("Invalid Link", "This invite link is broken or has expired.")
            "CHANNEL_PRIVATE" in u || "CHANNEL_INVALID" in u ->
                FriendlyError("Not Available", "Sorry, this channel is private or no longer available.")
            // ---- Input ----
            "USERNAME_OCCUPIED" in u || "USERNAME IS ALREADY" in u -> FriendlyError("Username Taken", "This username is already taken. Please choose another one.")
            "USERNAME_INVALID" in u -> FriendlyError("Invalid Username", "Usernames can use a–z, 0–9 and underscores, and must be at least 5 characters long.")
            "USERNAME_NOT_OCCUPIED" in u || "USERNAME NOT FOUND" in u -> FriendlyError("User Not Found", "There is no Telegram account with this username.")
            "MESSAGE_TOO_LONG" in u -> FriendlyError("Message Too Long", "Please make the message shorter and try again.")
            "MESSAGE_EMPTY" in u -> FriendlyError("Empty Message", "You can't send an empty message.")
            "MESSAGE_NOT_MODIFIED" in u -> FriendlyError("Nothing Changed", "The message is already the same.")
            "MESSAGE_EDIT_TIME_EXPIRED" in u -> FriendlyError("Can't Edit", "This message can no longer be edited.")
            "FILE_PARTS_INVALID" in u || "FILE_TOO_BIG" in u || "FILE IS TOO BIG" in u ->
                FriendlyError("File Too Large", "This file is too large to send.")
            "CAN'T BE SENT YET" in u || "NOT SUPPORTED" in u -> FriendlyError("Not Supported", sentence(e))
            // ---- Network ----
            "TIMEOUT" in u || "NETWORK" in u || "CONNECTION" in u || "DIDN'T RESPOND" in u ->
                FriendlyError("Connection Problem", if ("BOT" in u) sentence(e) else "Please check your internet connection and try again.")
            "NOT FOUND" in u || ("PEER" in u && "_INVALID" in u) -> FriendlyError("Not Found", sentence(e))
            // ---- Anything else ----
            Regex("""^[A-Z0-9_]+$""").matches(e) -> FriendlyError("Something Went Wrong", "Telegram couldn't complete this action ($e). Please try again.")
            // A short sentence of our own ("Couldn't save to the gallery") reads best as the alert's title alone.
            e.length <= 64 && e.count { it == '.' } <= 1 -> FriendlyError(e.trimEnd('.').replaceFirstChar { it.uppercase() }, null)
            else -> FriendlyError("Something Went Wrong", sentence(e))
        }
    }
}

/** A centered iOS alert with a single OK button. */
fun ActionSheetState.alert(title: String, message: String? = null, button: String = "OK", onDismiss: () -> Unit = {}) {
    val current = request
    // The same alert is already on screen (e.g. an error reported twice): don't flash it again.
    if (visible && current != null && current.alert && current.title == title && current.message == message) return
    show(SheetRequest(title = title, message = message, alert = true, cancel = null, actions = listOf(SheetAction(button, bold = true, onClick = onDismiss))))
}

/** Shows [raw] (a TDLib error or a short error sentence) as a friendly iOS alert. */
fun ActionSheetState.showError(raw: String, onDismiss: () -> Unit = {}) {
    val e = AppErrors.describe(raw)
    alert(e.title, e.message, onDismiss = onDismiss)
}

/** "Telegram Premium Required" alert for a feature the account can't use without Premium. */
fun ActionSheetState.premiumRequired(message: String = "This feature is available only to subscribers of Telegram Premium.") =
    alert("Telegram Premium Required", message)
