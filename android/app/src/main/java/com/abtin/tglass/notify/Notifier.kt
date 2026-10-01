package com.abtin.tglass.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.abtin.tglass.MainActivity
import com.abtin.tglass.R
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.initials

/** One incoming message as shown in a notification. */
data class NotifyMessage(
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    val group: Boolean,
    val senderId: Long,
    val senderName: String,
    val text: String,
    val date: Long,
    val senderAvatarPath: String?,
    val chatAvatarPath: String?,
)

/**
 * Posts Telegram-style message notifications: one per chat (MessagingStyle with the recent
 * messages), avatars, inline reply and "Mark as Read".
 */
object Notifier {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_SERVICE = "connection"
    const val EXTRA_CHAT_ID = "chat_id"
    const val KEY_REPLY = "reply_text"
    const val ACTION_REPLY = "com.abtin.tglass.REPLY"
    const val ACTION_READ = "com.abtin.tglass.READ"

    private val history = HashMap<Long, ArrayDeque<NotifyMessage>>()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages in your chats"
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Keeps TGlass connected so messages arrive while the app is closed"
            setShowBadge(false)
        })
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether the user allowed this app's notifications in system settings. */
    fun systemEnabled(context: Context): Boolean = canPost(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens Android's notification settings for this app. */
    fun openSystemSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Posts a sample notification so the user can check that notifications reach them. */
    fun showTest(context: Context) {
        history.remove(0L)
        show(
            context,
            NotifyMessage(
                chatId = 0L, messageId = System.currentTimeMillis(), chatTitle = "TGlass", group = false,
                senderId = 0L, senderName = "TGlass", text = "Notifications are working ✅",
                date = System.currentTimeMillis(), senderAvatarPath = null, chatAvatarPath = null,
            ),
        )
    }

    private fun notificationId(chatId: Long) = (chatId xor (chatId ushr 32)).toInt()

    fun openChatIntent(context: Context, chatId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, notificationId(chatId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, m: NotifyMessage) {
        if (!canPost(context)) return
        ensureChannels(context)
        val list = history.getOrPut(m.chatId) { ArrayDeque() }
        if (list.any { it.messageId == m.messageId }) return
        list.addLast(m)
        while (list.size > 7) list.removeFirst()

        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (m.group) m.chatTitle else null)
            .setGroupConversation(m.group)
        list.forEach { msg ->
            val person = Person.Builder()
                .setName(msg.senderName)
                .setKey(msg.senderId.toString())
                .setIcon(IconCompat.createWithBitmap(avatarBitmap(msg.senderName, msg.senderId, msg.senderAvatarPath)))
                .build()
            style.addMessage(msg.text, msg.date, person)
        }

        val id = notificationId(m.chatId)
        val reply = NotificationCompat.Action.Builder(
            0, "Reply",
            PendingIntent.getBroadcast(
                context, id,
                Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_REPLY).putExtra(EXTRA_CHAT_ID, m.chatId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
            .build()
        val read = NotificationCompat.Action.Builder(
            0, "Mark as Read",
            PendingIntent.getBroadcast(
                context, id + 1,
                Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_READ).putExtra(EXTRA_CHAT_ID, m.chatId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0088FF.toInt())
            .setStyle(style)
            .setLargeIcon(avatarBitmap(m.chatTitle, m.chatId, m.chatAvatarPath))
            .setContentIntent(openChatIntent(context, m.chatId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(m.date)
            .setNumber(list.size)
            .addAction(reply)
            .addAction(read)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, chatId: Long) {
        if (history.remove(chatId) != null) NotificationManagerCompat.from(context).cancel(notificationId(chatId))
    }

    fun cancelAll(context: Context) {
        history.keys.toList().forEach { cancel(context, it) }
    }

    /** Round avatar: the downloaded photo, or Telegram's gradient with initials. */
    private fun avatarBitmap(name: String, seed: Long, path: String?): Bitmap {
        val size = 128
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val photo = path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        if (photo != null) {
            val scaled = Bitmap.createScaledBitmap(photo, size, size, true)
            paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            return out
        }
        val (top, bottom) = avatarColors(seed)
        paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), top.toArgbInt(), bottom.toArgbInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        paint.textSize = size * 0.4f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials(name), size / 2f, y, paint)
        return out
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
    )
}
